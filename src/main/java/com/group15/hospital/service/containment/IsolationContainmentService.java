package com.group15.hospital.service.containment;

import com.group15.hospital.dao.AuditDAO;
import com.group15.hospital.dao.BedDAO;
import com.group15.hospital.dao.PatientDAO;
import com.group15.hospital.dao.WardTopologyDAO;
import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.bed.BedComponent;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.hierarchy.Patient;
import com.group15.hospital.model.isolation.WardTopologyGraph;
import com.group15.hospital.model.state.BedState;
import com.group15.hospital.model.state.RestrictedBufferState;
import com.group15.hospital.model.state.VacantState;
import com.group15.hospital.service.auxiliary.PatientTransferListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Service managing contagious infection isolation buffers and coordinating patient transfers.
 * <p>
 * Enforces infection containment rules (SRS F12 &amp; Use Case U4):
 * <ul>
 *   <li>When a contagious/airborne patient is placed in a bed, all adjacent beds and shared-air beds
 *       defined in the {@link WardTopologyGraph} that are {@code VACANT} are dynamically locked
 *       into {@link BedStatus#RESTRICTED_BUFFER} state.</li>
 *   <li>Beds in {@code RESTRICTED_BUFFER} state strictly reject new patient admissions or ambulance reservations.</li>
 *   <li>When the contagious patient is transferred or discharged, buffer beds are released back to
 *       {@link BedStatus#VACANT} — unless another adjacent contagious bed still requires them as a safety buffer
 *       (overlapping isolation zones).</li>
 *   <li>Notifies registered {@link PatientTransferListener} observers (such as {@code BillingService}
 *       and {@code KitchenService}) upon admissions, transfers, and discharges.</li>
 * </ul>
 * </p>
 *
 * @author Jatin (Module P3)
 * @see WardTopologyDAO
 * @see WardTopologyGraph
 * @see PatientTransferListener
 * @see BedState
 */
public class IsolationContainmentService {

    private static final Logger LOGGER = Logger.getLogger(IsolationContainmentService.class.getName());

    private final WardTopologyDAO wardTopologyDAO;
    private final BedDAO bedDAO;
    private final PatientDAO patientDAO;
    private final AuditDAO auditDAO;

    /** Registered observers for patient admission, transfer, and discharge events. */
    private final List<PatientTransferListener> listeners = new CopyOnWriteArrayList<>();

    /** Set of bed IDs currently housing an isolated contagious patient. */
    private final Set<Long> activeIsolatedBeds = ConcurrentHashMap.newKeySet();

    /** In-memory registry of BedComponent instances (BaseBed, decorated beds, etc.) for unit tests and runtime lookup. */
    private final Map<Long, BedComponent> registeredBeds = new ConcurrentHashMap<>();

    public IsolationContainmentService(WardTopologyDAO wardTopologyDAO, BedDAO bedDAO,
                                       PatientDAO patientDAO, AuditDAO auditDAO) {
        this.wardTopologyDAO = Objects.requireNonNull(wardTopologyDAO, "wardTopologyDAO must not be null");
        this.bedDAO = Objects.requireNonNull(bedDAO, "bedDAO must not be null");
        this.patientDAO = Objects.requireNonNull(patientDAO, "patientDAO must not be null");
        this.auditDAO = Objects.requireNonNull(auditDAO, "auditDAO must not be null");
    }

    public IsolationContainmentService() {
        this(new WardTopologyDAO(), new BedDAO(), new PatientDAO(), new AuditDAO());
    }

    // ────────────────────── Observer Registration ──────────────────────

    public void registerListener(PatientTransferListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(PatientTransferListener listener) {
        listeners.remove(listener);
    }

    public List<PatientTransferListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    // ────────────────────── In-Memory Bed Registry ──────────────────────

    public void registerBed(BedComponent bed) {
        if (bed != null) {
            registeredBeds.put(bed.getBedId(), bed);
        }
    }

    public BedComponent getBed(long bedId) {
        BedComponent mem = registeredBeds.get(bedId);
        if (mem != null) {
            return mem;
        }
        return bedDAO.findById(bedId).orElse(null);
    }

    public Set<Long> getActiveIsolatedBeds() {
        return Collections.unmodifiableSet(activeIsolatedBeds);
    }

    public boolean isBedIsolated(long bedId) {
        return activeIsolatedBeds.contains(bedId);
    }

    // ────────────────────── Containment Lock & Release ──────────────────────

    /**
     * Activates infection control isolation for a contagious bed.
     * <p>
     * Finds all direct physical neighbors and shared-air beds from the ward topology.
     * Any connected bed that is currently {@link BedStatus#VACANT} is locked into
     * {@link BedStatus#RESTRICTED_BUFFER} state via {@link VacantState#restrictBuffer()}.
     * </p>
     *
     * @param contagiousBedId ID of the bed housing the contagious patient
     * @param actor           user or system role activating isolation
     * @return set of newly locked buffer bed IDs
     */
    public Set<Long> activateIsolation(long contagiousBedId, String actor) {
        activeIsolatedBeds.add(contagiousBedId);
        WardTopologyGraph graph = wardTopologyDAO.loadTopologyGraph();
        Set<Long> affectedBeds = graph.getAllAffectedBufferBeds(contagiousBedId);
        Set<Long> newlyLocked = new HashSet<>();

        String operator = (actor != null && !actor.trim().isEmpty()) ? actor.trim() : "INFECTION_CONTROL";

        for (Long neighborId : affectedBeds) {
            BedComponent bed = getBed(neighborId);
            if (bed != null && bed.getBedStatus() == BedStatus.VACANT) {
                // Apply State pattern transition from VACANT to RESTRICTED_BUFFER
                BedState newState = new VacantState().restrictBuffer();
                bed.setBedStatus(newState.getStatus());
                bedDAO.updateBedStatus(neighborId, BedStatus.RESTRICTED_BUFFER);
                newlyLocked.add(neighborId);

                auditDAO.log(
                        operator,
                        "ISOLATION_BUFFER_LOCKED",
                        String.valueOf(neighborId),
                        String.format("Bed #%d transitioned to %s as an infection safety buffer for contagious bed #%d",
                                neighborId, newState.getStatus(), contagiousBedId)
                );
            }
        }

        LOGGER.info(String.format("[IsolationContainmentService] Activated isolation on bed #%d, locked %d buffer bed(s): %s",
                contagiousBedId, newlyLocked.size(), newlyLocked));
        return newlyLocked;
    }

    /**
     * Releases infection control isolation for a bed when a contagious patient departs.
     * <p>
     * Only unlocks a buffer bed to {@link BedStatus#VACANT} if it is <b>not</b> adjacent
     * to any other remaining active isolated bed (handling overlapping isolation zones).
     * Validates transition via {@link RestrictedBufferState#releaseBuffer()}.
     * </p>
     *
     * @param contagiousBedId ID of the bed that was previously isolated
     * @param actor           user or system role releasing isolation
     * @return set of released bed IDs returned to VACANT
     */
    public Set<Long> releaseIsolation(long contagiousBedId, String actor) {
        activeIsolatedBeds.remove(contagiousBedId);
        WardTopologyGraph graph = wardTopologyDAO.loadTopologyGraph();
        Set<Long> affectedBeds = graph.getAllAffectedBufferBeds(contagiousBedId);
        Set<Long> releasedBeds = new HashSet<>();

        String operator = (actor != null && !actor.trim().isEmpty()) ? actor.trim() : "INFECTION_CONTROL";

        for (Long bufferId : affectedBeds) {
            // Check if this buffer bed is still exposed to any OTHER active isolated bed
            boolean stillExposed = false;
            for (Long remainingIsolatedId : activeIsolatedBeds) {
                Set<Long> otherBuffers = graph.getAllAffectedBufferBeds(remainingIsolatedId);
                if (otherBuffers.contains(bufferId)) {
                    stillExposed = true;
                    break;
                }
            }

            if (!stillExposed) {
                BedComponent bed = getBed(bufferId);
                if (bed != null && bed.getBedStatus() == BedStatus.RESTRICTED_BUFFER) {
                    // Apply State pattern transition from RESTRICTED_BUFFER back to VACANT
                    BedState newState = new RestrictedBufferState().releaseBuffer();
                    bed.setBedStatus(newState.getStatus());
                    bedDAO.updateBedStatus(bufferId, BedStatus.VACANT);
                    releasedBeds.add(bufferId);

                    auditDAO.log(
                            operator,
                            "ISOLATION_BUFFER_RELEASED",
                            String.valueOf(bufferId),
                            String.format("Bed #%d released back to %s after contagious patient departed from bed #%d",
                                    bufferId, newState.getStatus(), contagiousBedId)
                    );
                }
            }
        }

        LOGGER.info(String.format("[IsolationContainmentService] Released isolation on bed #%d, freed %d buffer bed(s): %s",
                contagiousBedId, releasedBeds.size(), releasedBeds));
        return releasedBeds;
    }

    // ────────────────────── Lifecycle & Transfer Orchestration ──────────────────────

    /**
     * Admits a patient to a bed, optionally activating contagious isolation containment,
     * and broadcasts the event to all registered {@link PatientTransferListener}s.
     *
     * @param patient    the patient to admit
     * @param targetBed  the bed component
     * @param contagious whether patient requires airborne/contact infection isolation
     * @param actor      staff username admitting patient
     * @throws IllegalBedStateException if targetBed cannot accept patients (e.g. RESTRICTED_BUFFER)
     */
    public void admitPatient(Patient patient, BedComponent targetBed, boolean contagious, String actor) {
        Objects.requireNonNull(patient, "patient must not be null");
        Objects.requireNonNull(targetBed, "targetBed must not be null");

        registerBed(targetBed);

        // Attempt assignment — will throw IllegalBedStateException if bed is RESTRICTED_BUFFER or OCCUPIED
        targetBed.assignPatient(patient.getPatientId(), patient.getFullName(), patient.getTriageLevel());

        // Update database states
        bedDAO.updateBedStatus(targetBed.getBedId(), BedStatus.OCCUPIED);
        patient.assignBed(targetBed.getBedId());
        patientDAO.updateAssignedBed(patient.getPatientId(), targetBed.getBedId());

        String operator = (actor != null) ? actor : "ADMISSION_DESK";

        auditDAO.log(
                operator,
                "PATIENT_ADMITTED",
                String.valueOf(patient.getPatientId()),
                String.format("Admitted patient '%s' to bed #%d [%s]. Contagious: %b",
                        patient.getFullName(), targetBed.getBedId(), targetBed.getWardName(), contagious)
        );

        if (contagious) {
            activateIsolation(targetBed.getBedId(), operator);
        }

        // Notify observers
        for (PatientTransferListener listener : listeners) {
            try {
                listener.onPatientAdmitted(patient, targetBed);
            } catch (Exception ex) {
                LOGGER.warning("Listener onPatientAdmitted failed: " + ex.getMessage());
            }
        }
    }

    /**
     * Transfers an admitted patient from one bed to another, releasing old isolation buffers,
     * establishing new isolation buffers if needed, and notifying all registered observers.
     *
     * @param patient     the patient being transferred
     * @param fromBed     source bed
     * @param toBed       destination bed
     * @param hoursStayed billable duration in source bed
     * @param contagious  whether infection containment is required on destination bed
     * @param actor       staff username executing the transfer
     */
    public void transferPatient(Patient patient, BedComponent fromBed, BedComponent toBed,
                                long hoursStayed, boolean contagious, String actor) {
        Objects.requireNonNull(patient, "patient must not be null");
        Objects.requireNonNull(fromBed, "fromBed must not be null");
        Objects.requireNonNull(toBed, "toBed must not be null");

        registerBed(fromBed);
        registerBed(toBed);

        // 1. Assign to new bed (validates state)
        toBed.assignPatient(patient.getPatientId(), patient.getFullName(), patient.getTriageLevel());

        // 2. Discharge from old bed
        fromBed.dischargePatient(); // transitions to REQUIRES_SANITIZATION
        bedDAO.dischargeBed(fromBed.getBedId());

        String operator = (actor != null) ? actor : "TRANSFER_COORDINATOR";

        // 3. Release old isolation if previously active
        if (activeIsolatedBeds.contains(fromBed.getBedId())) {
            releaseIsolation(fromBed.getBedId(), operator);
        }

        // 4. Activate new isolation if contagious
        if (contagious) {
            activateIsolation(toBed.getBedId(), operator);
        }

        // 5. Update patient bed reference
        patient.assignBed(toBed.getBedId());
        patientDAO.updateAssignedBed(patient.getPatientId(), toBed.getBedId());
        bedDAO.updateBedStatus(toBed.getBedId(), BedStatus.OCCUPIED);

        auditDAO.log(
                operator,
                "PATIENT_TRANSFERRED",
                String.valueOf(patient.getPatientId()),
                String.format("Patient '%s' transferred from bed #%d [%s] to bed #%d [%s] after %d hrs",
                        patient.getFullName(), fromBed.getBedId(), fromBed.getWardName(),
                        toBed.getBedId(), toBed.getWardName(), hoursStayed)
        );

        // 6. Notify observers (Billing & Kitchen)
        for (PatientTransferListener listener : listeners) {
            try {
                listener.onPatientTransferred(patient, fromBed, toBed, hoursStayed);
            } catch (Exception ex) {
                LOGGER.warning("Listener onPatientTransferred failed: " + ex.getMessage());
            }
        }
    }

    /**
     * Discharges a patient from the hospital, releasing isolation buffers if any,
     * transitioning the bed to REQUIRES_SANITIZATION, and notifying observers.
     *
     * @param patient     the patient being discharged
     * @param releasedBed the bed vacated
     * @param hoursStayed billable duration in final bed
     * @param actor       staff username processing discharge
     */
    public void dischargePatient(Patient patient, BedComponent releasedBed, long hoursStayed, String actor) {
        Objects.requireNonNull(patient, "patient must not be null");
        Objects.requireNonNull(releasedBed, "releasedBed must not be null");

        registerBed(releasedBed);

        String operator = (actor != null) ? actor : "DISCHARGE_DESK";

        // 1. Bed transitions to REQUIRES_SANITIZATION
        releasedBed.dischargePatient();
        bedDAO.dischargeBed(releasedBed.getBedId());

        // 2. Release isolation if bed was isolated
        if (activeIsolatedBeds.contains(releasedBed.getBedId())) {
            releaseIsolation(releasedBed.getBedId(), operator);
        }

        // 3. Update patient record
        patient.discharge();
        patientDAO.dischargePatient(patient.getPatientId());

        auditDAO.log(
                operator,
                "PATIENT_DISCHARGED",
                String.valueOf(patient.getPatientId()),
                String.format("Discharged patient '%s' from bed #%d [%s] after %d hrs final stay",
                        patient.getFullName(), releasedBed.getBedId(), releasedBed.getWardName(), hoursStayed)
        );

        // 4. Notify observers
        for (PatientTransferListener listener : listeners) {
            try {
                listener.onPatientDischarged(patient, releasedBed, hoursStayed);
            } catch (Exception ex) {
                LOGGER.warning("Listener onPatientDischarged failed: " + ex.getMessage());
            }
        }
    }
}
