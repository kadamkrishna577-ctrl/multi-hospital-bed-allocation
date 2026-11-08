package com.group15.hospital.service.preemption;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.dao.BedDAO;
import com.group15.hospital.exception.BedAlreadyBookedException;
import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.bed.BaseBed;
import com.group15.hospital.model.bed.WaitlistEntry;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.hierarchy.AuditLogEntry;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe service for <strong>emergency bed pre-emption</strong>.
 * <p>
 * When a critically ill patient (typically {@link TriageLevel#LEVEL_1 LEVEL_1})
 * arrives and all beds are occupied, this service can forcibly displace a
 * lower-acuity patient ({@link TriageLevel#LEVEL_4 LEVEL_4} or
 * {@link TriageLevel#LEVEL_5 LEVEL_5}) and move them onto the waitlist.
 * </p>
 *
 * <h3>Thread Safety</h3>
 * A per-bed {@link ReentrantLock} (keyed by bed ID in a
 * {@link ConcurrentHashMap}) ensures that only one thread can modify
 * a given bed at a time, preventing race conditions during booking
 * and pre-emption.
 *
 * @author Group 15
 * @see TriageLevel#canSwap(TriageLevel)
 * @see WaitlistManager
 * @see BedDAO
 */
public class EmergencyPreemptionService {

    private static final Logger LOGGER = Logger.getLogger(EmergencyPreemptionService.class.getName());

    /** Per-bed locks to serialise concurrent operations on the same bed. */
    private final ConcurrentHashMap<Long, ReentrantLock> bedLocks = new ConcurrentHashMap<>();

    private final BedDAO bedDAO;
    private final WaitlistManager waitlistManager;

    // ──────────────────────────── Constructor ─────────────────────────────

    /**
     * Creates a new preemption service backed by the given DAO and waitlist manager.
     *
     * @param bedDAO          DAO for bed persistence
     * @param waitlistManager thread-safe waitlist manager
     */
    public EmergencyPreemptionService(BedDAO bedDAO, WaitlistManager waitlistManager) {
        this.bedDAO = Objects.requireNonNull(bedDAO, "bedDAO must not be null");
        this.waitlistManager = Objects.requireNonNull(waitlistManager, "waitlistManager must not be null");
    }

    // ─────────────────────────── Inner Record ────────────────────────────

    /**
     * Immutable result of a pre-emption or allocation attempt.
     *
     * @param success          whether the operation succeeded
     * @param message          human-readable result description
     * @param bedId            the bed that was targeted
     * @param displacedPatient the name of the displaced patient (null if none)
     * @param auditLogEntry    the audit trail entry for this action
     */
    public record PreemptionResult(
            boolean success,
            String message,
            long bedId,
            String displacedPatient,
            AuditLogEntry auditLogEntry
    ) {}

    // ──────────────────────── Pre-emption Logic ──────────────────────────

    /**
     * Attempts to pre-empt the occupant of a bed with a higher-acuity patient.
     * <p>
     * <strong>Workflow:</strong>
     * <ol>
     *   <li>Acquires the per-bed lock for {@code bedId}.</li>
     *   <li>Loads the bed from the database.</li>
     *   <li>Validates the incoming triage level can swap with the current occupant
     *       via {@link TriageLevel#canSwap(TriageLevel)}.</li>
     *   <li>Displaces the current patient onto the waitlist.</li>
     *   <li>Books the bed for the emergency patient inside a database transaction.</li>
     *   <li>Creates an {@link AuditLogEntry} recording the action.</li>
     * </ol>
     *
     * @param bedId                the target bed
     * @param emergencyPatientId   the emergency patient's ID
     * @param emergencyPatientName the emergency patient's display name
     * @param incomingLevel        the emergency patient's triage level
     * @return a {@link PreemptionResult} describing the outcome
     * @throws BedAlreadyBookedException if the bed state changed concurrently
     */
    public PreemptionResult preemptBed(long bedId, long emergencyPatientId,
                                       String emergencyPatientName, TriageLevel incomingLevel) {
        Objects.requireNonNull(emergencyPatientName, "emergencyPatientName must not be null");
        Objects.requireNonNull(incomingLevel, "incomingLevel must not be null");

        ReentrantLock lock = bedLocks.computeIfAbsent(bedId, id -> new ReentrantLock(true));
        lock.lock();
        try {
            return executePreemption(bedId, emergencyPatientId, emergencyPatientName, incomingLevel);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Core pre-emption logic — called while holding the bed lock.
     */
    private PreemptionResult executePreemption(long bedId, long emergencyPatientId,
                                               String emergencyPatientName, TriageLevel incomingLevel) {
        // 1. Load current bed state from DB
        Optional<BaseBed> optBed = bedDAO.findById(bedId);
        if (optBed.isEmpty()) {
            String msg = "Bed #" + bedId + " not found in the database.";
            LOGGER.warning(msg);
            return new PreemptionResult(false, msg, bedId, null, null);
        }

        BaseBed bed = optBed.get();

        // 2. If bed is vacant, just book it directly
        if (bed.getBedStatus() == BedStatus.VACANT) {
            return attemptDirectAllocation(bedId, emergencyPatientId,
                    emergencyPatientName, incomingLevel);
        }

        // 3. Bed must be OCCUPIED for preemption
        if (bed.getBedStatus() != BedStatus.OCCUPIED) {
            String msg = "Bed #" + bedId + " is in state " + bed.getBedStatus() +
                         " and cannot be pre-empted.";
            LOGGER.warning(msg);
            return new PreemptionResult(false, msg, bedId, null, null);
        }

        // 4. Check triage swap eligibility
        TriageLevel occupantLevel = bed.getCurrentTriageLevel();
        if (occupantLevel == null) {
            // Check PatientDAO for active admitted patient on this bed
            try {
                com.group15.hospital.dao.PatientDAO pDAO = new com.group15.hospital.dao.PatientDAO();
                for (com.group15.hospital.model.hierarchy.Patient p : pDAO.findAdmittedPatients()) {
                    if (p.getAssignedBedId() != null && p.getAssignedBedId() == bedId) {
                        occupantLevel = p.getTriageLevel();
                        bed.forceAssignPatient(p.getPatientId(), p.getFullName(), occupantLevel);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (occupantLevel == null) {
            // Default to LEVEL_5 (Non-urgent occupant) if patient info unrecorded
            occupantLevel = TriageLevel.LEVEL_5;
        }

        if (!incomingLevel.canSwap(occupantLevel)) {
            String msg = String.format(
                    "Pre-emption denied: incoming %s (%s) cannot displace occupant %s (%s) on bed #%d.",
                    emergencyPatientName, incomingLevel.getDescription(),
                    bed.getCurrentPatientName(), occupantLevel.getDescription(), bedId);
            LOGGER.info(msg);
            return new PreemptionResult(false, msg, bedId, null, null);
        }

        // 5. Execute the swap inside a transaction
        String displacedPatientName = bed.getCurrentPatientName();
        Long displacedPatientId = bed.getCurrentPatientId();

        final TriageLevel finalOccupantLevel = occupantLevel;

        try {
            DatabaseConnection.getInstance().executeTransactionVoid(conn -> {
                // 5a. Discharge current patient
                bedDAO.dischargeBed(bedId);

                // 5b. Add displaced patient to waitlist
                WaitlistEntry waitlistEntry = new WaitlistEntry(
                        displacedPatientId != null ? displacedPatientId : 0L,
                        displacedPatientName != null ? displacedPatientName : "Unknown",
                        finalOccupantLevel,
                        bed.getHospitalName(),
                        bed.getWardName(),
                        "Displaced by emergency pre-emption for patient: " + emergencyPatientName
                );
                waitlistManager.addToWaitlist(waitlistEntry);

                // 5c. Book bed for emergency patient (optimistic locking on REQUIRES_SANITIZATION)
                bedDAO.bookBed(bedId, emergencyPatientId, emergencyPatientName,
                              incomingLevel, BedStatus.REQUIRES_SANITIZATION);
            });
        } catch (BedAlreadyBookedException e) {
            LOGGER.log(Level.WARNING, "Concurrent modification during pre-emption of bed #" + bedId, e);
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "DB transaction unavailable, executing in-memory pre-emption: " + e.getMessage());
            try {
                bedDAO.dischargeBed(bedId);
                WaitlistEntry waitlistEntry = new WaitlistEntry(
                        displacedPatientId != null ? displacedPatientId : 0L,
                        displacedPatientName != null ? displacedPatientName : "Unknown",
                        finalOccupantLevel,
                        bed.getHospitalName(),
                        bed.getWardName(),
                        "Displaced by emergency pre-emption for patient: " + emergencyPatientName
                );
                waitlistManager.addToWaitlist(waitlistEntry);
                bedDAO.bookBed(bedId, emergencyPatientId, emergencyPatientName,
                              incomingLevel, BedStatus.REQUIRES_SANITIZATION);
            } catch (Exception ex2) {
                String msg = "Pre-emption failed for bed #" + bedId + ": " + ex2.getMessage();
                LOGGER.log(Level.SEVERE, msg, ex2);
                return new PreemptionResult(false, msg, bedId, null, null);
            }
        }

        // 6. Audit log
        AuditLogEntry auditEntry = new AuditLogEntry(
                "SYSTEM",
                "EMERGENCY_PREEMPTION",
                String.valueOf(bedId),
                String.format("Patient '%s' (triage=%s) pre-empted '%s' (triage=%s) from bed #%d",
                        emergencyPatientName, incomingLevel.getDescription(),
                        displacedPatientName, occupantLevel.getDescription(), bedId)
        );

        String successMsg = String.format(
                "Pre-emption successful: '%s' (%s) now occupies bed #%d. " +
                "'%s' (%s) has been moved to the waitlist.",
                emergencyPatientName, incomingLevel.getDescription(), bedId,
                displacedPatientName, occupantLevel.getDescription());
        LOGGER.info(successMsg);

        return new PreemptionResult(true, successMsg, bedId, displacedPatientName, auditEntry);
    }

    // ──────────────────── Direct Allocation (no swap) ────────────────────

    /**
     * Attempts to directly allocate a vacant bed to a patient.
     * <p>
     * Acquires the per-bed lock, verifies the bed is {@link BedStatus#VACANT},
     * and books it atomically via {@link BedDAO#bookBed}.
     * </p>
     *
     * @param bedId       the target bed
     * @param patientId   the patient's ID
     * @param patientName the patient's display name
     * @param level       the patient's triage level
     * @return a {@link PreemptionResult} describing the outcome
     */
    public PreemptionResult attemptDirectAllocation(long bedId, long patientId,
                                                    String patientName, TriageLevel level) {
        Objects.requireNonNull(patientName, "patientName must not be null");
        Objects.requireNonNull(level, "level must not be null");

        ReentrantLock lock = bedLocks.computeIfAbsent(bedId, id -> new ReentrantLock(true));
        boolean alreadyHeld = lock.isHeldByCurrentThread();

        if (!alreadyHeld) {
            lock.lock();
        }
        try {
            // Verify bed is vacant
            Optional<BaseBed> optBed = bedDAO.findById(bedId);
            if (optBed.isEmpty()) {
                return new PreemptionResult(false, "Bed #" + bedId + " not found.", bedId, null, null);
            }

            BaseBed bed = optBed.get();
            if (bed.getBedStatus() != BedStatus.VACANT) {
                return new PreemptionResult(false,
                        "Bed #" + bedId + " is not vacant (status=" + bed.getBedStatus() + ").",
                        bedId, null, null);
            }

            // Book with optimistic locking
            try {
                bedDAO.bookBed(bedId, patientId, patientName, level, BedStatus.VACANT);
            } catch (BedAlreadyBookedException e) {
                return new PreemptionResult(false,
                        "Bed #" + bedId + " was booked by another user concurrently.",
                        bedId, null, null);
            }

            AuditLogEntry auditEntry = new AuditLogEntry(
                    "SYSTEM", "BED_ALLOCATION", String.valueOf(bedId),
                    String.format("Patient '%s' (triage=%s) allocated to bed #%d",
                            patientName, level.getDescription(), bedId)
            );

            String msg = String.format("Bed #%d allocated to patient '%s' (%s).",
                    bedId, patientName, level.getDescription());
            LOGGER.info(msg);
            return new PreemptionResult(true, msg, bedId, null, auditEntry);

        } finally {
            if (!alreadyHeld) {
                lock.unlock();
            }
        }
    }
}
