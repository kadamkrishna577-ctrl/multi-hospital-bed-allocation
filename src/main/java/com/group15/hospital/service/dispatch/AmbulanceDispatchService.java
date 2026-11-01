package com.group15.hospital.service.dispatch;

import com.group15.hospital.dao.AmbulanceDAO;
import com.group15.hospital.dao.AlertDAO;
import com.group15.hospital.exception.BedAlreadyBookedException;
import com.group15.hospital.model.ambulance.Ambulance;
import com.group15.hospital.model.ambulance.AmbulanceReservation;
import com.group15.hospital.model.ambulance.SystemAlert;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.enums.UserRole;
import com.group15.hospital.model.state.BedState;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Core dispatch service for ambulance reservation, partner hospital rerouting,
 * and 15-minute expiry background scheduling (SRS F5, F8, F9, F13 & Use Case U5).
 * <p>
 * <b>Performance guarantees per SRS Section 4.1:</b>
 * <ul>
 *   <li>Ambulance reservation and capacity-loss rerouting: {@code < 1.5 seconds}</li>
 *   <li>Background timer CPU overhead: {@code < 5%} via a single-thread daemon scheduler</li>
 *   <li>Thread safety for 50 concurrent terminals via {@link AmbulanceDAO}'s per-bed
 *       {@code ReentrantLock} + ACID transactions</li>
 * </ul>
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class AmbulanceDispatchService {

    private static final Logger LOGGER = Logger.getLogger(AmbulanceDispatchService.class.getName());

    /** How frequently the expiry daemon sweeps for stale 15-minute reservations (seconds). */
    private static final int EXPIRY_SWEEP_INTERVAL_SECONDS = 30;

    // ── Collaborators ──
    private final AmbulanceDAO ambulanceDAO;
    private final AlertDAO alertDAO;
    private final AlertNotificationManager notificationManager;

    // ── Background Daemon (< 5% CPU overhead by virtue of 30-second interval) ──
    private final ScheduledExecutorService expiryScheduler;

    // ── In-memory simulated partner hospital bed store (for offline / test use) ──
    // Maps hospitalName -> (bedId -> BedStatus), used when BedDAO is unavailable
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, BedStatus>> simulatedHospitalBeds
            = new ConcurrentHashMap<>();

    /**
     * Constructs the dispatch service and immediately starts the background expiry daemon.
     *
     * @param ambulanceDAO        DAO for ambulance and reservation operations
     * @param alertDAO            DAO for alert persistence
     * @param notificationManager observer hub for real-time pop-up delivery
     */
    public AmbulanceDispatchService(
            AmbulanceDAO ambulanceDAO,
            AlertDAO alertDAO,
            AlertNotificationManager notificationManager
    ) {
        this.ambulanceDAO = Objects.requireNonNull(ambulanceDAO, "ambulanceDAO must not be null");
        this.alertDAO = Objects.requireNonNull(alertDAO, "alertDAO must not be null");
        this.notificationManager = Objects.requireNonNull(notificationManager, "notificationManager must not be null");

        // Daemon thread so it doesn't prevent JVM shutdown (< 5% CPU overhead at 30-second intervals)
        this.expiryScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "AmbulanceReservationExpiryDaemon");
            t.setDaemon(true);
            return t;
        });

        startExpiryDaemon();
    }

    // ──────────────────── Ambulance Reservation (< 1.5 s) ─────────────────

    /**
     * Reserves the best available bed at the preferred hospital for an incoming ambulance (SRS F5, F8 & U5).
     * <p>
     * Validates ambulance availability and clinical severity compatibility, then atomically locks
     * the best-matching vacant bed and persists the reservation. Broadcasts an
     * {@code AMBULANCE_RESERVED} alert via the Observer system.
     * </p>
     *
     * @param ambulanceId        the vehicle identifier
     * @param triageLevel        patient clinical acuity
     * @param equipmentNeeds     required onboard equipment (may be empty)
     * @param preferredHospital  target hospital name
     * @param patientName        patient display name
     * @return the created {@link AmbulanceReservation}
     * @throws IllegalStateException     if the ambulance is unavailable or not found
     * @throws BedAlreadyBookedException if no vacant beds are available at the hospital
     */
    public AmbulanceReservation requestBedReservation(
            String ambulanceId,
            TriageLevel triageLevel,
            Set<EquipmentType> equipmentNeeds,
            String preferredHospital,
            String patientName
    ) {
        Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        Objects.requireNonNull(triageLevel, "triageLevel must not be null");
        Objects.requireNonNull(preferredHospital, "preferredHospital must not be null");

        long startMs = System.currentTimeMillis();

        // 1. Look up ambulance
        Ambulance ambulance = ambulanceDAO.findAmbulanceById(ambulanceId)
                .orElseThrow(() -> new IllegalStateException("Ambulance not found: " + ambulanceId));

        if (!ambulance.isAvailable()) {
            throw new IllegalStateException("Ambulance " + ambulanceId + " is not currently available.");
        }

        // 2. Severity compatibility and equipment warning check (SRS F9 & U5 Exception 3)
        Set<EquipmentType> needs = equipmentNeeds != null ? equipmentNeeds : Collections.emptySet();
        Optional<String> warning = ambulance.getSeverityWarning(triageLevel, needs);
        if (warning.isPresent()) {
            LOGGER.warning("[Dispatch] Severity mismatch warning for ambulance " + ambulanceId
                    + " with patient triage " + triageLevel + ": " + warning.get());
            // Broadcast a high-priority compatibility warning alert to triage DOCTOR role
            SystemAlert warnAlert = new SystemAlert(
                    SystemAlert.TYPE_NO_CAPACITY, UserRole.DOCTOR, preferredHospital, null,
                    "AMBULANCE MISMATCH: " + warning.get()
            );
            notificationManager.broadcastAlert(warnAlert);
        }

        // 3. Find the best available bed at the preferred hospital
        long bestBedId = pickBestVacantBed(preferredHospital, triageLevel);
        if (bestBedId < 0) {
            throw new BedAlreadyBookedException(
                    "No vacant beds available at " + preferredHospital + " for triage " + triageLevel);
        }

        // 4. Atomic reservation (thread-safe, < 1.5 s per SRS Section 4.1)
        String wardName = resolveWardForTriage(triageLevel);
        AmbulanceReservation reservation = new AmbulanceReservation(
                ambulanceId, ambulance.getAmbulanceType(),
                (patientName != null && !patientName.isBlank()) ? patientName.trim() : "Emergency Patient (Unidentified)",
                triageLevel, needs, preferredHospital, wardName, bestBedId
        );
        AmbulanceReservation saved = ambulanceDAO.createReservationAtomic(reservation);

        // Update in-memory index in service so subsequent searches select other vacant beds
        ConcurrentHashMap<Long, BedStatus> localBeds = simulatedHospitalBeds.get(preferredHospital);
        if (localBeds != null) {
            localBeds.put(bestBedId, BedStatus.RESERVED_EN_ROUTE);
        }

        // 5. Mark ambulance as dispatched
        ambulanceDAO.updateAmbulanceAvailability(ambulanceId, false, preferredHospital);

        // 6. Apply State Pattern transition: VACANT -> RESERVED_EN_ROUTE
        BedState state = BedState.fromStatus(BedStatus.VACANT);
        BedState nextState = state.reserveEnRoute();
        LOGGER.fine("[Dispatch] State transition: " + state.getStatus() + " -> " + nextState.getStatus()
                + " for bed #" + bestBedId);

        // 7. Broadcast AMBULANCE_RESERVED alert to triage NURSE role (< 0.5 s via Observer)
        SystemAlert reservedAlert = new SystemAlert(
                SystemAlert.TYPE_AMBULANCE_RESERVED, UserRole.NURSE, preferredHospital, bestBedId,
                String.format("Ambulance %s (%s) en route for %s patient '%s'. Bed #%d in %s reserved for 15 minutes.",
                        ambulanceId, ambulance.getAmbulanceType(), triageLevel.getDescription(),
                        saved.getPatientName(), bestBedId, wardName)
        );
        notificationManager.broadcastAlert(reservedAlert);

        long elapsed = System.currentTimeMillis() - startMs;
        LOGGER.info(String.format("[Dispatch] Reservation #%d created in %d ms (target < 1500 ms) — bed #%d @ %s",
                saved.getReservationId(), elapsed, bestBedId, preferredHospital));

        return saved;
    }

    // ──────────────── Partner Hospital Rerouting (< 1.5 s) ───────────────

    /**
     * Checks if the target hospital is at capacity and reroutes the ambulance to the nearest
     * partner hospital with a compatible vacant bed (SRS F9 & U5 Alt Flow 1).
     *
     * @param reservationId the existing in-flight reservation
     * @return the updated {@link AmbulanceReservation} pointing to the new partner hospital
     * @throws BedAlreadyBookedException if no partner hospital has a compatible bed
     */
    public AmbulanceReservation checkAndRerouteIfFull(long reservationId) {
        return checkAndRerouteIfFull(reservationId, false);
    }

    /**
     * Checks if the target hospital is at capacity (or forcefully simulates capacity loss)
     * and reroutes the ambulance to the nearest partner hospital (SRS F9 & U5 Alt Flow 1).
     * <p>
     * The search strictly excludes the original destination hospital
     * (!candidate.equalsIgnoreCase(currentHospital)) to guarantee that a reroute always shifts
     * the patient to a different partner facility.
     * </p>
     *
     * @param reservationId the existing in-flight reservation
     * @param forceReroute  if true, skips the original hospital vacancy check and triggers partner rerouting
     * @return the updated {@link AmbulanceReservation} pointing to the new partner hospital
     * @throws BedAlreadyBookedException if no partner hospital has a compatible bed
     */
    public AmbulanceReservation checkAndRerouteIfFull(long reservationId, boolean forceReroute) {
        long startMs = System.currentTimeMillis();

        AmbulanceReservation existing = ambulanceDAO.findReservationById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation #" + reservationId + " not found"));

        if (AmbulanceReservation.STATUS_ARRIVED.equalsIgnoreCase(existing.getStatus())) {
            throw new IllegalStateException("Reservation #" + reservationId + " has already arrived at destination and cannot be rerouted.");
        }

        String currentHospital = existing.getHospitalName();
        TriageLevel triage = existing.getPatientTriage();

        // 1. If not forced, check if original hospital still has space
        if (!forceReroute) {
            long stillAvailable = pickBestVacantBed(currentHospital, triage);
            if (stillAvailable >= 0) {
                LOGGER.fine("[Reroute] Hospital " + currentHospital + " still has capacity — no reroute needed.");
                return existing;
            }
        }

        LOGGER.info("[Reroute] Hospital " + currentHospital + " at capacity (or forced) — searching partner hospitals...");

        // 2. Find partner hospitals — strictly excluding current hospital
        List<String> partners = getPartnerHospitals(currentHospital);
        for (String partner : partners) {
            if (partner == null || partner.trim().equalsIgnoreCase(currentHospital.trim())) {
                continue; // Strictly guarantee reroute shifts to a DIFFERENT partner facility
            }

            long partnerBed = pickBestVacantBed(partner, triage);
            if (partnerBed >= 0) {
                int etaMinutes = AmbulanceReservation.DEFAULT_WINDOW_MINUTES;
                String wardName = resolveWardForTriage(triage);

                AmbulanceReservation rerouted = ambulanceDAO.rerouteReservationAtomic(
                        reservationId, partner, wardName, partnerBed, etaMinutes
                );

                // Update in-memory index if bed is tracked
                ConcurrentHashMap<Long, BedStatus> oldBeds = simulatedHospitalBeds.get(currentHospital);
                if (oldBeds != null) {
                    oldBeds.put(existing.getAssignedBedId(), BedStatus.VACANT);
                }
                ConcurrentHashMap<Long, BedStatus> beds = simulatedHospitalBeds.get(partner);
                if (beds != null) {
                    beds.put(partnerBed, BedStatus.RESERVED_EN_ROUTE);
                }

                // Broadcast AMBULANCE_REROUTED alert to triage screens at BOTH hospitals
                String alertMsg = String.format(
                        "REROUTE: Ambulance %s redirected from %s (capacity reached) → %s (bed #%d/%s). Patient: %s [%s].",
                        existing.getAmbulanceId(), currentHospital, partner, partnerBed,
                        wardName, existing.getPatientName(), triage.getDescription()
                );
                notificationManager.broadcastAlert(new SystemAlert(
                        SystemAlert.TYPE_AMBULANCE_REROUTED, UserRole.NURSE, partner, partnerBed, alertMsg));
                notificationManager.broadcastAlert(new SystemAlert(
                        SystemAlert.TYPE_AMBULANCE_REROUTED, UserRole.NURSE, currentHospital, null, alertMsg));

                long elapsed = System.currentTimeMillis() - startMs;
                LOGGER.info(String.format("[Reroute] Reservation #%d rerouted from %s to %s in %d ms (target < 1500 ms)",
                        reservationId, currentHospital, partner, elapsed));
                return rerouted;
            }
        }

        // No capacity anywhere — broadcast critical no-capacity alert
        SystemAlert noCapAlert = new SystemAlert(
                SystemAlert.TYPE_NO_CAPACITY, UserRole.DOCTOR, currentHospital, null,
                "CRITICAL: No partner hospitals have available beds for ambulance "
                        + existing.getAmbulanceId() + " carrying " + triage.getDescription() + " patient "
                        + existing.getPatientName() + ". Manual coordination required immediately!"
        );
        notificationManager.broadcastAlert(noCapAlert);

        throw new BedAlreadyBookedException(
                "No beds available at any partner hospital for reservation #" + reservationId);
    }

    /**
     * Simulates a bed at a hospital for offline / unit testing. In production this
     * reads from {@code BedDAO} (Krishna's module).
     */
    public void seedSimulatedBed(String hospitalName, long bedId, BedStatus status) {
        simulatedHospitalBeds
                .computeIfAbsent(hospitalName, k -> new ConcurrentHashMap<>())
                .put(bedId, status);
        // Mirror into AmbulanceDAO's simulated store for atomic operations
        ambulanceDAO.seedBedForTesting(bedId, status);
    }

    // ──────────────────── Background Expiry Daemon ────────────────────────

    /**
     * Starts the single-threaded daemon that sweeps every 30 seconds for over-due 15-minute
     * ambulance reservations. CPU overhead is well under 5% due to the long polling interval.
     */
    private void startExpiryDaemon() {
        expiryScheduler.scheduleAtFixedRate(
                this::runExpirySweep,
                EXPIRY_SWEEP_INTERVAL_SECONDS,    // initial delay
                EXPIRY_SWEEP_INTERVAL_SECONDS,    // period
                TimeUnit.SECONDS
        );
        LOGGER.info("[ExpiryDaemon] Started — sweeping every " + EXPIRY_SWEEP_INTERVAL_SECONDS + " seconds.");
    }

    /**
     * Executed by the daemon: finds overdue reservations, releases their beds, and fires alerts.
     * This method is package-private so unit tests can invoke it directly with a custom timestamp.
     */
    void runExpirySweep() {
        runExpirySweepAt(LocalDateTime.now());
    }

    /**
     * Parameterised expiry sweep for deterministic testing.
     *
     * @param now the effective current time for expiry evaluation
     */
    public void runExpirySweepAt(LocalDateTime now) {
        try {
            List<AmbulanceReservation> expired = ambulanceDAO.expireOverdueReservations(now);
            for (AmbulanceReservation res : expired) {
                // Sync in-memory bed store in service if tracked
                ConcurrentHashMap<Long, BedStatus> beds = simulatedHospitalBeds.get(res.getHospitalName());
                if (beds != null) {
                    beds.put(res.getAssignedBedId(), BedStatus.VACANT);
                }

                // Release ambulance back to available fleet
                ambulanceDAO.updateAmbulanceAvailability(res.getAmbulanceId(), true, res.getHospitalName());

                // Alert triage staff that the hold window lapsed
                SystemAlert expiredAlert = new SystemAlert(
                        SystemAlert.TYPE_RESERVATION_EXPIRED, UserRole.NURSE, res.getHospitalName(),
                        res.getAssignedBedId(),
                        String.format("Reservation #%d for ambulance %s EXPIRED — bed #%d at %s/%s released to VACANT. " +
                                "Patient '%s' [%s] was not admitted.",
                                res.getReservationId(), res.getAmbulanceId(), res.getAssignedBedId(),
                                res.getHospitalName(), res.getWardName(),
                                res.getPatientName(), res.getPatientTriage().getDescription())
                );
                notificationManager.broadcastAlert(expiredAlert);

                LOGGER.info("[ExpiryDaemon] Expired reservation #" + res.getReservationId()
                        + " — bed #" + res.getAssignedBedId() + " returned to VACANT.");
            }
            if (!expired.isEmpty()) {
                LOGGER.info("[ExpiryDaemon] Sweep complete — expired " + expired.size() + " reservation(s).");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ExpiryDaemon] Unexpected error during expiry sweep", e);
        }
    }

    /**
     * Gracefully shuts down the background expiry scheduler (call on application close).
     */
    public void shutdown() {
        expiryScheduler.shutdown();
        try {
            if (!expiryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                expiryScheduler.shutdownNow();
            }
            LOGGER.info("[ExpiryDaemon] Scheduler shut down cleanly.");
        } catch (InterruptedException ie) {
            expiryScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ──────────────────────────── Helpers ─────────────────────────────────

    /**
     * Finds the best available vacant bed at the given hospital for the specified triage level.
     * In production this delegates to {@code BedDAO.findAvailableBeds(hospitalName)}.
     * Falls back to the in-memory simulated store when MySQL is offline.
     *
     * @return a valid bed ID, or {@code -1} if none available
     */
    private long pickBestVacantBed(String hospitalName, TriageLevel triage) {
        // Check AmbulanceDAO's test bed store (synced during seedSimulatedBed calls)
        ConcurrentHashMap<Long, BedStatus> hospitalBeds = simulatedHospitalBeds.get(hospitalName);
        if (hospitalBeds != null) {
            for (Map.Entry<Long, BedStatus> entry : hospitalBeds.entrySet()) {
                if (entry.getValue() == BedStatus.VACANT && ambulanceDAO.getBedStatus(entry.getKey()) == BedStatus.VACANT) {
                    return entry.getKey();
                }
            }
        }

        // In a fully wired application, this would call:
        //   bedDAO.findAvailableBeds(hospitalName).stream().findFirst().map(BaseBed::getBedId).orElse(-1L);
        return -1L;
    }

    /** Maps a partner hospital list — strictly excluding the current hospital (case-insensitive). */
    private List<String> getPartnerHospitals(String excludeHospital) {
        Set<String> all = new LinkedHashSet<>(simulatedHospitalBeds.keySet());
        all.add("City Central Hospital");
        all.add("Memorial Care Hospital");
        all.add("Metro General Hospital");
        all.add("St. Jude Emergency Center");

        return all.stream()
                .filter(h -> h != null && (excludeHospital == null || !h.trim().equalsIgnoreCase(excludeHospital.trim())))
                .collect(Collectors.toList());
    }

    /** Resolves the appropriate ward name based on clinical triage acuity. */
    private String resolveWardForTriage(TriageLevel triage) {
        return switch (triage) {
            case LEVEL_1, LEVEL_2 -> "ICU";
            case LEVEL_3 -> "Emergency Ward";
            default -> "General Ward";
        };
    }
}
