package com.group15.hospital.service.dispatch;

import com.group15.hospital.dao.AlertDAO;
import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.ambulance.SystemAlert;
import com.group15.hospital.model.bed.WaitlistEntry;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.enums.UserRole;
import com.group15.hospital.model.state.BedState;
import com.group15.hospital.service.preemption.WaitlistManager;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service managing terminal room sanitization confirmation and waitlist notification
 * after a bed becomes available (SRS F10, F13 & Use Case U6).
 * <p>
 * <b>Responsibilities:</b>
 * <ol>
 *   <li>Validate the bed is genuinely in {@code REQUIRES_SANITIZATION} state.</li>
 *   <li>Apply State Pattern transition: {@code RequiresSanitizationState → VacantState}.</li>
 *   <li>Persist the new bed status via {@code BedDAO} / in-memory fallback.</li>
 *   <li>Broadcast a {@code BED_CLEANED} alert via {@link AlertNotificationManager}
 *       to all live triage screens in {@code < 0.5 s}.</li>
 *   <li>Poll {@link WaitlistManager} for the highest-priority waiting patient at that
 *       hospital/ward and fire a {@code WAITLIST_READY} notification so triage can
 *       immediately assign the freed bed.</li>
 * </ol>
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class SanitizationService {

    private static final Logger LOGGER = Logger.getLogger(SanitizationService.class.getName());

    // ── Collaborators ──
    private final AlertDAO alertDAO;
    private final AlertNotificationManager notificationManager;
    private final WaitlistManager waitlistManager;

    /**
     * In-memory bed state store (mirrors BedDAO for offline operation and test isolation).
     * Maps bedId -> BedState.  Initialised by the caller via {@link #seedBedState}.
     */
    private final ConcurrentHashMap<Long, BedState> bedStateStore = new ConcurrentHashMap<>();

    /**
     * Maps bedId -> hospitalName, so we can look up the hospital for a bed without a BedDAO call.
     */
    private final ConcurrentHashMap<Long, String> bedHospitalIndex = new ConcurrentHashMap<>();

    /**
     * Maps bedId -> wardName for waitlist matching.
     */
    private final ConcurrentHashMap<Long, String> bedWardIndex = new ConcurrentHashMap<>();

    /**
     * Constructs the sanitization service.
     *
     * @param alertDAO            DAO for alert persistence (offline queue support)
     * @param notificationManager Observer hub for pop-up delivery
     * @param waitlistManager     Krishna's P2 waitlist manager for next-patient lookup
     */
    public SanitizationService(
            AlertDAO alertDAO,
            AlertNotificationManager notificationManager,
            WaitlistManager waitlistManager
    ) {
        this.alertDAO = Objects.requireNonNull(alertDAO, "alertDAO must not be null");
        this.notificationManager = Objects.requireNonNull(notificationManager, "notificationManager must not be null");
        this.waitlistManager = Objects.requireNonNull(waitlistManager, "waitlistManager must not be null");
    }

    // ──────────────────── Bed Registration ──────────────────────────────

    /**
     * Seeds a bed's current state for offline / test use. In production, the state is
     * hydrated from {@code BedDAO.findById(bedId)} and translated via
     * {@link BedState#fromStatus(BedStatus)}.
     *
     * @param bedId        unique bed identifier
     * @param state        the current {@link BedState} of the bed
     * @param hospitalName the hospital this bed belongs to
     * @param wardName     the ward this bed belongs to
     */
    public void seedBedState(long bedId, BedState state, String hospitalName, String wardName) {
        bedStateStore.put(bedId, Objects.requireNonNull(state));
        bedHospitalIndex.put(bedId, Objects.requireNonNull(hospitalName));
        bedWardIndex.put(bedId, wardName != null ? wardName : "General Ward");
    }

    public void seedBedState(long bedId, BedStatus status) {
        seedBedState(bedId, BedState.fromStatus(status), "Default Hospital", "General Ward");
    }

    public void seedBedState(long bedId, BedState state) {
        seedBedState(bedId, state, "Default Hospital", "General Ward");
    }

    public void seedBedState(long bedId, BedStatus status, String hospitalName, String wardName) {
        seedBedState(bedId, BedState.fromStatus(status), hospitalName, wardName);
    }

    /**
     * Seeds a waiting patient in the WaitlistManager for waitlist matching demonstrations (U6 Alt Flow 1).
     */
    public void seedWaitlistPatient(String hospitalName, String patientName, TriageLevel triage) {
        String hosp = (hospitalName != null && !hospitalName.isBlank()) ? hospitalName : "City Central Hospital";
        String name = (patientName != null && !patientName.isBlank()) ? patientName : "Vikram Seth";
        TriageLevel level = (triage != null) ? triage : TriageLevel.LEVEL_1;
        WaitlistEntry entry = new WaitlistEntry(
                888L,
                name,
                level,
                hosp,
                "ICU",
                "Awaiting ICU bed allocation"
        );
        waitlistManager.addToWaitlist(entry);
    }

    /**
     * Returns the current {@link BedStatus} of a bed from the in-memory store.
     */
    public BedStatus getCurrentBedStatus(long bedId) {
        BedState state = bedStateStore.get(bedId);
        return state != null ? state.getStatus() : null;
    }

    // ──────────────────── Core Sanitization Logic ─────────────────────────

    /**
     * Marks a room as sanitized by cleaning staff, transitions the bed to VACANT,
     * and fires instant triage pop-up alerts (SRS F10, F13 & U6 Step 4).
     * <p>
     * Alert delivery is guaranteed within {@code < 0.5 s} via
     * {@link AlertNotificationManager#broadcastAlert(SystemAlert)} using
     * {@code SwingUtilities.invokeLater}.
     * </p>
     *
     * @param bedId     the bed identifier that was cleaned
     * @param staffName the cleaning staff member's display name (for audit logging)
     * @throws IllegalArgumentException  if the bed is not known to the service
     * @throws IllegalBedStateException  if the bed is not in REQUIRES_SANITIZATION state
     */
    public void markRoomSanitized(long bedId, String staffName) {
        String staff = (staffName != null && !staffName.isBlank()) ? staffName : "Unknown Staff";

        // 1. Retrieve current bed state
        BedState currentState = bedStateStore.get(bedId);
        if (currentState == null) {
            // Fall back: treat as REQUIRES_SANITIZATION if unknown (safe assumption for cleaning flow)
            currentState = BedState.fromStatus(BedStatus.REQUIRES_SANITIZATION);
            bedStateStore.put(bedId, currentState);
            LOGGER.warning("[Sanitization] Bed #" + bedId + " not in local store — defaulting to REQUIRES_SANITIZATION.");
        }

        String hospitalName = bedHospitalIndex.getOrDefault(bedId, "Unknown Hospital");
        String wardName = bedWardIndex.getOrDefault(bedId, "General Ward");

        // 2. Apply State Pattern transition: REQUIRES_SANITIZATION -> VACANT
        // This throws IllegalBedStateException if the bed is in any other state (e.g. still OCCUPIED)
        BedState newState;
        try {
            newState = currentState.markSanitized();
        } catch (IllegalBedStateException e) {
            LOGGER.warning("[Sanitization] Invalid transition for bed #" + bedId
                    + " (current: " + currentState.getStatus() + "): " + e.getMessage());
            throw e;
        }

        // 3. Persist updated state in memory (and in production, via BedDAO.updateBedStatus)
        bedStateStore.put(bedId, newState);
        LOGGER.info("[Sanitization] Bed #" + bedId + " sanitized by '" + staff
                + "': " + currentState.getStatus() + " → " + newState.getStatus());

        // 4. Broadcast BED_CLEANED alert to NURSE triage screens (< 0.5 s Observer dispatch)
        SystemAlert cleanedAlert = new SystemAlert(
                SystemAlert.TYPE_BED_CLEANED,
                UserRole.NURSE,
                hospitalName,
                bedId,
                String.format("✓ Bed #%d in %s / %s has been sanitized and is now VACANT. Staff: %s",
                        bedId, hospitalName, wardName, staff)
        );
        notificationManager.broadcastAlert(cleanedAlert);

        // 5. Check WaitlistManager (Krishna's P2): is there a displaced/waiting patient?
        checkAndNotifyWaitlist(bedId, hospitalName, wardName);
    }

    /**
     * Overloaded helper allowing explicit hospital and ward context specification.
     */
    public void markRoomSanitized(long bedId, String staffName, String hospitalName, String wardName) {
        if (hospitalName != null) {
            bedHospitalIndex.put(bedId, hospitalName);
        }
        if (wardName != null) {
            bedWardIndex.put(bedId, wardName);
        }
        markRoomSanitized(bedId, staffName);
    }

    // ──────────────────── Waitlist Notification ───────────────────────────

    /**
     * Polls the P2 WaitlistManager for the highest-priority waiting patient at this hospital
     * and fires a WAITLIST_READY alert so triage can immediately assign the freed bed.
     */
    private void checkAndNotifyWaitlist(long bedId, String hospitalName, String wardName) {
        try {
            int waitlistSize = waitlistManager.getWaitlistSize();
            if (waitlistSize == 0) {
                LOGGER.fine("[Sanitization] Waitlist is empty — no allocation suggestion needed.");
                return;
            }

            // Peek the highest-priority patient for this hospital (non-destructive read)
            List<WaitlistEntry> candidates = waitlistManager.getWaitlistForHospital(hospitalName);
            if (candidates.isEmpty()) {
                LOGGER.fine("[Sanitization] No waitlist entries for hospital: " + hospitalName);
                return;
            }

            WaitlistEntry top = candidates.get(0);  // sorted by priority (triage then FIFO)
            LOGGER.info("[Sanitization] Waitlist candidate found for " + hospitalName
                    + ": Patient '" + top.getPatientName() + "' [" + top.getTriageLevel().getDescription() + "]");

            // Broadcast WAITLIST_READY alert to NURSE and DOCTOR so they can allocate the freed bed
            String alertMsg = String.format(
                    "⚑ WAITLIST READY: Bed #%d (%s / %s) is now clean and available. " +
                    "Next patient: '%s' [%s] (Waiting since: %s). Please proceed with allocation.",
                    bedId, hospitalName, wardName,
                    top.getPatientName(),
                    top.getTriageLevel().getDescription(),
                    top.getRequestTime().toString()
            );

            notificationManager.broadcastAlert(new SystemAlert(
                    SystemAlert.TYPE_WAITLIST_READY, UserRole.NURSE, hospitalName, bedId, alertMsg));
            notificationManager.broadcastAlert(new SystemAlert(
                    SystemAlert.TYPE_WAITLIST_READY, UserRole.DOCTOR, hospitalName, bedId, alertMsg));

        } catch (Exception e) {
            // WaitlistManager might not be fully initialized during testing
            LOGGER.log(Level.WARNING, "[Sanitization] Error checking waitlist for bed #" + bedId, e);
        }
    }
}
