package com.group15.hospital;

import com.group15.hospital.dao.AlertDAO;
import com.group15.hospital.dao.AmbulanceDAO;
import com.group15.hospital.dao.WaitlistDAO;
import com.group15.hospital.exception.BedAlreadyBookedException;
import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.ambulance.ALSAmbulance;
import com.group15.hospital.model.ambulance.Ambulance;
import com.group15.hospital.model.ambulance.AmbulanceReservation;
import com.group15.hospital.model.ambulance.BLSAmbulance;
import com.group15.hospital.model.ambulance.SystemAlert;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.enums.UserRole;
import com.group15.hospital.model.state.BedState;
import com.group15.hospital.model.state.OccupiedState;
import com.group15.hospital.model.state.RequiresSanitizationState;
import com.group15.hospital.model.state.ReservedEnRouteState;
import com.group15.hospital.model.state.RestrictedBufferState;
import com.group15.hospital.model.state.VacantState;
import com.group15.hospital.service.dispatch.AlertNotificationManager;
import com.group15.hospital.service.dispatch.AlertObserver;
import com.group15.hospital.service.dispatch.AmbulanceDispatchService;
import com.group15.hospital.service.dispatch.SanitizationService;
import com.group15.hospital.service.preemption.WaitlistManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Unit and Concurrency Test Suite for Module P4.
 * <p>
 * Validates State Pattern bed lifecycle, illegal transition guards,
 * 50-thread atomic concurrency race protection, clinical ALS/BLS suitability,
 * 15-minute reservation ETA window extension and background daemon expiry sweep,
 * partner hospital dynamic rerouting SLA (< 1.5s), and room sanitization Observer alerts (< 0.5s).
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class AmbulanceAndStateTest {

    private AlertDAO alertDAO;
    private AmbulanceDAO ambulanceDAO;
    private AlertNotificationManager notificationManager;
    private WaitlistManager waitlistManager;
    private SanitizationService sanitizationService;
    private AmbulanceDispatchService dispatchService;

    @BeforeAll
    public static void initHeadlessEnvironment() {
        System.setProperty("java.awt.headless", "true");
    }

    @BeforeEach
    public void setUp() {
        AlertNotificationManager.resetForTesting();
        alertDAO = new AlertDAO();
        ambulanceDAO = new AmbulanceDAO();
        notificationManager = AlertNotificationManager.getInstance(alertDAO);
        waitlistManager = new WaitlistManager(new WaitlistDAO(), true); // skip DB load for testing
        sanitizationService = new SanitizationService(alertDAO, notificationManager, waitlistManager);
        dispatchService = new AmbulanceDispatchService(ambulanceDAO, alertDAO, notificationManager);
    }

    @AfterEach
    public void tearDown() {
        if (dispatchService != null) {
            dispatchService.shutdown();
        }
        AlertNotificationManager.resetForTesting();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 1: Complete State Pattern Lifecycle (SRS F8, F10, U5, U6)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 1: Complete State Pattern Lifecycle and Transitions")
    public void testValidBedStateTransitions() {
        // Step A: VACANT initial state
        BedState vacant = BedState.fromStatus(BedStatus.VACANT);
        assertInstanceOf(VacantState.class, vacant);
        assertEquals(BedStatus.VACANT, vacant.getStatus());
        assertTrue(vacant.canReserveEnRoute());
        assertTrue(vacant.canAcceptPatient());
        assertNotNull(vacant.getStateDescription());

        // Step B: VACANT -> reserveEnRoute() -> RESERVED_EN_ROUTE
        BedState reserved = vacant.reserveEnRoute();
        assertInstanceOf(ReservedEnRouteState.class, reserved);
        assertEquals(BedStatus.RESERVED_EN_ROUTE, reserved.getStatus());
        assertFalse(reserved.canReserveEnRoute());
        assertTrue(reserved.canAcceptPatient());

        // Step C: RESERVED_EN_ROUTE -> assignPatient() -> OCCUPIED
        BedState occupied = reserved.assignPatient();
        assertInstanceOf(OccupiedState.class, occupied);
        assertEquals(BedStatus.OCCUPIED, occupied.getStatus());
        assertFalse(occupied.canReserveEnRoute());
        assertFalse(occupied.canAcceptPatient());

        // Step D: OCCUPIED -> dischargePatient() -> REQUIRES_SANITIZATION
        BedState dirty = occupied.dischargePatient();
        assertInstanceOf(RequiresSanitizationState.class, dirty);
        assertEquals(BedStatus.REQUIRES_SANITIZATION, dirty.getStatus());
        assertFalse(dirty.canReserveEnRoute());
        assertFalse(dirty.canAcceptPatient());

        // Step E: REQUIRES_SANITIZATION -> markSanitized() -> VACANT
        BedState sanitized = dirty.markSanitized();
        assertInstanceOf(VacantState.class, sanitized);
        assertEquals(BedStatus.VACANT, sanitized.getStatus());
        assertTrue(sanitized.canReserveEnRoute());
        assertTrue(sanitized.canAcceptPatient());

        // Step F: Buffer Quarantine Cycle (VACANT -> RESTRICTED_BUFFER -> VACANT)
        BedState buffer = vacant.restrictBuffer();
        assertInstanceOf(RestrictedBufferState.class, buffer);
        assertEquals(BedStatus.RESTRICTED_BUFFER, buffer.getStatus());
        assertFalse(buffer.canReserveEnRoute());
        assertFalse(buffer.canAcceptPatient());

        BedState unbuffered = buffer.releaseBuffer();
        assertInstanceOf(VacantState.class, unbuffered);
        assertEquals(BedStatus.VACANT, unbuffered.getStatus());

        // Step G: Reservation Expiry / Cancellation Cycle (RESERVED_EN_ROUTE -> VACANT)
        BedState reserved2 = BedState.fromStatus(BedStatus.VACANT).reserveEnRoute();
        BedState cancelled = reserved2.cancelOrExpireReservation();
        assertInstanceOf(VacantState.class, cancelled);
        assertEquals(BedStatus.VACANT, cancelled.getStatus());

        // Step H: BedState.fromStatus factory mapping for all 5 enum values
        for (BedStatus status : BedStatus.values()) {
            BedState state = BedState.fromStatus(status);
            assertNotNull(state, "fromStatus must not return null for " + status);
            assertEquals(status, state.getStatus(), "BedState must reflect status " + status);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 2: Illegal State Transition Guards (SRS F10, F12, U6 Ex. 2)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 2: Illegal State Transitions Throw IllegalBedStateException")
    public void testIllegalBedStateTransitionsThrowException() {
        BedState vacant = BedState.fromStatus(BedStatus.VACANT);
        BedState occupied = BedState.fromStatus(BedStatus.OCCUPIED);
        BedState reserved = BedState.fromStatus(BedStatus.RESERVED_EN_ROUTE);
        BedState dirty = BedState.fromStatus(BedStatus.REQUIRES_SANITIZATION);
        BedState buffer = BedState.fromStatus(BedStatus.RESTRICTED_BUFFER);

        // Guard A: assignPatient() and reserveEnRoute() on OCCUPIED
        assertThrows(IllegalBedStateException.class, occupied::assignPatient,
                "OCCUPIED bed cannot assign new patient directly");
        assertThrows(IllegalBedStateException.class, occupied::reserveEnRoute,
                "OCCUPIED bed cannot be reserved en-route");

        // Guard B: assignPatient() and reserveEnRoute() on REQUIRES_SANITIZATION
        assertThrows(IllegalBedStateException.class, dirty::assignPatient,
                "Unsanitized bed cannot accept a patient");
        assertThrows(IllegalBedStateException.class, dirty::reserveEnRoute,
                "Unsanitized bed cannot be reserved en-route");

        // Guard C: assignPatient() and reserveEnRoute() on RESTRICTED_BUFFER
        assertThrows(IllegalBedStateException.class, buffer::assignPatient,
                "Quarantine buffer bed cannot accept patient before release");
        assertThrows(IllegalBedStateException.class, buffer::reserveEnRoute,
                "Quarantine buffer bed cannot be reserved en-route");

        // Guard D: markSanitized() on non-sanitization states
        assertThrows(IllegalBedStateException.class, vacant::markSanitized,
                "VACANT bed does not require sanitization");
        assertThrows(IllegalBedStateException.class, occupied::markSanitized,
                "OCCUPIED bed cannot be marked sanitized while patient is present");
        assertThrows(IllegalBedStateException.class, reserved::markSanitized,
                "RESERVED_EN_ROUTE bed cannot be marked sanitized");

        // Guard E: dischargePatient() on beds without an active patient
        assertThrows(IllegalBedStateException.class, vacant::dischargePatient);
        assertThrows(IllegalBedStateException.class, reserved::dischargePatient);
        assertThrows(IllegalBedStateException.class, dirty::dischargePatient);

        // Guard F: releaseBuffer() on non-buffer beds
        assertThrows(IllegalBedStateException.class, vacant::releaseBuffer);
        assertThrows(IllegalBedStateException.class, occupied::releaseBuffer);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 3: 50-Thread Concurrency Race Condition (SRS 4.1 & F5)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 3: 50 Concurrent Threads Competing for 1 Bed — Zero Double Booking")
    public void testFiftyConcurrentAmbulanceReservationsNoDoubleBooking() throws InterruptedException {
        final long contestedBedId = 9999L;
        ambulanceDAO.seedBedForTesting(contestedBedId, BedStatus.VACANT, "City Central Hospital");

        final int threadCount = 50;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch doneGate = new CountDownLatch(threadCount);

        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger alreadyBookedCount = new AtomicInteger(0);
        final List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    startGate.await(); // Synchronize all 50 threads to fire simultaneously

                    AmbulanceReservation res = new AmbulanceReservation(
                            "AMB-CONC-" + id,
                            "ALS",
                            "Emergency Patient " + id,
                            TriageLevel.LEVEL_1,
                            Collections.emptySet(),
                            "City Central Hospital",
                            "ICU",
                            contestedBedId
                    );

                    ambulanceDAO.createReservationAtomic(res);
                    successCount.incrementAndGet();
                } catch (BedAlreadyBookedException e) {
                    alreadyBookedCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedExceptions.add(t);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // Fire all 50 threads at the exact same millisecond
        startGate.countDown();
        boolean finished = doneGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All 50 threads should complete within 10 seconds");
        assertTrue(unexpectedExceptions.isEmpty(), "Unexpected exceptions occurred: " + unexpectedExceptions);

        // Strict Concurrency Assertions
        assertEquals(1, successCount.get(), "Exactly one reservation must win the race");
        assertEquals(49, alreadyBookedCount.get(), "Exactly 49 threads must be safely rejected with BedAlreadyBookedException");
        assertEquals(BedStatus.RESERVED_EN_ROUTE, ambulanceDAO.getBedStatus(contestedBedId),
                "Contested bed must now be locked in RESERVED_EN_ROUTE state");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 4: ALS/BLS Severity Matching & Warnings (SRS F9 & U5 Ex. 3)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 4: ALS/BLS Acuity Matching and Clinical Warning Generation")
    public void testAmbulanceSeverityCompatibilityAndWarnings() {
        // 1. ALS Vehicle Verification
        ALSAmbulance als = new ALSAmbulance("AMB-ALS-501", "KA-01-EQ-5001", "City Central Hospital");
        assertEquals("ALS", als.getAmbulanceType());
        assertTrue(als.isSeverityCompatible(TriageLevel.LEVEL_1), "ALS must be compatible with Level 1 (Critical)");
        assertTrue(als.isSeverityCompatible(TriageLevel.LEVEL_2), "ALS must be compatible with Level 2 (Emergency)");
        assertTrue(als.isSeverityCompatible(TriageLevel.LEVEL_3), "ALS must be compatible with Level 3 (Urgent)");

        // ALS onboard equipment check
        assertTrue(als.getSupportedEquipment().contains(EquipmentType.VENTILATOR));
        assertTrue(als.getSupportedEquipment().contains(EquipmentType.OXYGEN));
        assertTrue(als.getSupportedEquipment().contains(EquipmentType.MONITOR));

        // Critical cardiac/ventilator patient assigned to ALS -> no clinical warning
        Optional<String> alsWarning = als.getSeverityWarning(TriageLevel.LEVEL_1, Set.of(EquipmentType.VENTILATOR, EquipmentType.MONITOR));
        assertTrue(alsWarning.isEmpty(), "ALS ambulance should produce zero clinical warnings for Level 1 ventilator cases");

        // 2. BLS Vehicle Verification
        BLSAmbulance bls = new BLSAmbulance("AMB-BLS-502", "KA-01-TX-5002", "City Central Hospital");
        assertEquals("BLS", bls.getAmbulanceType());
        assertFalse(bls.isSeverityCompatible(TriageLevel.LEVEL_1), "BLS must NOT be compatible with Level 1");
        assertFalse(bls.isSeverityCompatible(TriageLevel.LEVEL_2), "BLS must NOT be compatible with Level 2");
        assertTrue(bls.isSeverityCompatible(TriageLevel.LEVEL_3), "BLS is compatible with Level 3");
        assertTrue(bls.isSeverityCompatible(TriageLevel.LEVEL_4), "BLS is compatible with Level 4");
        assertTrue(bls.isSeverityCompatible(TriageLevel.LEVEL_5), "BLS is compatible with Level 5");

        // BLS assigned to Level 1 cardiac/trauma case -> High-Risk warning produced
        Optional<String> blsLevel1Warning = bls.getSeverityWarning(TriageLevel.LEVEL_1, Collections.emptySet());
        assertTrue(blsLevel1Warning.isPresent(), "BLS carrying Level 1 patient must trigger clinical warning");
        assertTrue(blsLevel1Warning.get().contains("HIGH-RISK WARNING") || blsLevel1Warning.get().contains("LEVEL_1"));

        // BLS assigned to patient needing VENTILATOR -> Equipment mismatch warning produced
        Optional<String> blsVentWarning = bls.getSeverityWarning(TriageLevel.LEVEL_3, Set.of(EquipmentType.VENTILATOR));
        assertTrue(blsVentWarning.isPresent(), "BLS lacking required ventilator must trigger critical mismatch warning");
        assertTrue(blsVentWarning.get().contains("CRITICAL MISMATCH") || blsVentWarning.get().contains("VENTILATOR"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 5: 15-Minute Window Extension & Expiry Sweep (SRS F8, U5 Alt 2, Ex 2)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 5: 15-Minute ETA Window Extension and Automatic Expiry Daemon Sweep")
    public void testReservationWindowExtensionAndExpirySweep() {
        final long bedId = 5001L;
        dispatchService.seedSimulatedBed("City Central Hospital", bedId, BedStatus.VACANT);

        // 1. Create an active reservation
        AmbulanceReservation reservation = dispatchService.requestBedReservation(
                "AMB-ALS-101",
                TriageLevel.LEVEL_2,
                Collections.emptySet(),
                "City Central Hospital",
                "Jane Doe"
        );
        assertNotNull(reservation);
        assertEquals(AmbulanceReservation.STATUS_RESERVED, reservation.getStatus());
        LocalDateTime initialExpiry = reservation.getExpiresAt();

        // 2. Paramedic reports traffic delay -> extend window by 10 minutes
        boolean extended = ambulanceDAO.extendReservationWindow(reservation.getReservationId(), 10);
        assertTrue(extended, "Extension should return true for active reservation");

        AmbulanceReservation updatedRes = ambulanceDAO.findReservationById(reservation.getReservationId())
                .orElseThrow(() -> new AssertionError("Reservation not found after extension"));
        assertEquals(initialExpiry.plusMinutes(10), updatedRes.getExpiresAt(),
                "ExpiresAt timestamp should be increased by exactly 10 minutes");

        // 3. Simulate overdue reservation and trigger expiry sweep
        LocalDateTime futureTime = updatedRes.getExpiresAt().plusMinutes(2);
        dispatchService.runExpirySweepAt(futureTime);

        // Verify reservation is marked EXPIRED
        AmbulanceReservation expiredRes = ambulanceDAO.findReservationById(reservation.getReservationId())
                .orElseThrow(() -> new AssertionError("Reservation not found after expiry sweep"));
        assertEquals(AmbulanceReservation.STATUS_EXPIRED, expiredRes.getStatus(),
                "Overdue reservation must transition to STATUS_EXPIRED");

        // Verify held bed returned to VACANT
        assertEquals(BedStatus.VACANT, ambulanceDAO.getBedStatus(bedId),
                "Held bed must automatically return to VACANT after expiration");

        // Verify vehicle is released back to available fleet
        Ambulance amb = ambulanceDAO.findAmbulanceById("AMB-ALS-101").orElseThrow();
        assertTrue(amb.isAvailable(), "Ambulance must be restored to available status upon reservation expiry");

        // Verify RESERVATION_EXPIRED alert broadcast
        List<SystemAlert> alerts = alertDAO.findAlertsForHospital("City Central Hospital");
        assertTrue(alerts.stream().anyMatch(a -> SystemAlert.TYPE_RESERVATION_EXPIRED.equals(a.getAlertType())),
                "RESERVATION_EXPIRED alert must be recorded in AlertDAO");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 6: Dynamic Partner Hospital Rerouting SLA (SRS 4.1, F9, U5 Alt 1)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 6: Partner Hospital Dynamic Rerouting Under 1.5s SLA")
    public void testDynamicPartnerHospitalReroutingUnderSla() {
        final String originHospital = "City Central Hospital";
        final String partnerHospital = "Memorial Care Hospital";
        final long originBedId = 6001L;
        final long partnerBedId = 6002L;

        // Seed beds
        dispatchService.seedSimulatedBed(originHospital, originBedId, BedStatus.VACANT);
        dispatchService.seedSimulatedBed(partnerHospital, partnerBedId, BedStatus.VACANT);

        // Make initial reservation at origin hospital
        AmbulanceReservation reservation = dispatchService.requestBedReservation(
                "AMB-ALS-101",
                TriageLevel.LEVEL_1,
                Collections.emptySet(),
                originHospital,
                "Critical Trauma Patient"
        );
        assertEquals(originHospital, reservation.getHospitalName());
        assertEquals(originBedId, reservation.getAssignedBedId());

        // Capacity loss occurs at origin hospital -> trigger rerouting
        long startMs = System.currentTimeMillis();
        AmbulanceReservation rerouted = dispatchService.checkAndRerouteIfFull(reservation.getReservationId(), true);
        long elapsedMs = System.currentTimeMillis() - startMs;

        // SLA Performance Verification: target < 1500 ms (SRS 4.1)
        assertTrue(elapsedMs < 1500, "Rerouting SLA violated: elapsed " + elapsedMs + " ms, expected < 1500 ms");

        // Reroute Destination Verification
        assertNotNull(rerouted, "Rerouted reservation must not be null");
        assertFalse(rerouted.getHospitalName().equalsIgnoreCase(originHospital),
                "Rerouted hospital must strictly differ from original full hospital");
        assertEquals(partnerHospital, rerouted.getHospitalName(),
                "Reservation must be rerouted to available partner hospital");
        assertEquals(originHospital, rerouted.getReroutedFromHospital(),
                "Original hospital must be recorded in rerouted_from_hospital audit field");

        // Bed State Transition Verification
        assertEquals(BedStatus.VACANT, ambulanceDAO.getBedStatus(originBedId),
                "Original bed at origin hospital must be released to VACANT");
        assertEquals(BedStatus.RESERVED_EN_ROUTE, ambulanceDAO.getBedStatus(rerouted.getAssignedBedId()),
                "Partner bed at destination hospital must be locked as RESERVED_EN_ROUTE");

        // Alert Verification: AMBULANCE_REROUTED alert dispatched
        List<SystemAlert> partnerAlerts = alertDAO.findAlertsForHospital(partnerHospital);
        assertTrue(partnerAlerts.stream().anyMatch(a -> SystemAlert.TYPE_AMBULANCE_REROUTED.equals(a.getAlertType())),
                "AMBULANCE_REROUTED alert must be logged for destination partner hospital");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 7: Sanitization, Observer Alert SLA (< 0.5s) & Offline Queue Replay
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 7: Room Sanitization, Observer Alert SLA (<0.5s), and Offline Queue Replay")
    public void testSanitizationAndObserverAlertDelivery() throws InterruptedException {
        final String hospital = "City Central Hospital";
        final long bedId = 7001L;

        // 1. Seed bed in REQUIRES_SANITIZATION state
        sanitizationService.seedBedState(bedId, BedState.fromStatus(BedStatus.REQUIRES_SANITIZATION), hospital, "ICU Ward");
        assertEquals(BedStatus.REQUIRES_SANITIZATION, sanitizationService.getCurrentBedStatus(bedId));

        // 2. Register mock AlertObserver with CountDownLatch to measure alert SLA
        CountDownLatch alertLatch = new CountDownLatch(1);
        AtomicReference<SystemAlert> deliveredAlert = new AtomicReference<>();

        AlertObserver observer = alert -> {
            if (SystemAlert.TYPE_BED_CLEANED.equals(alert.getAlertType())) {
                deliveredAlert.set(alert);
                alertLatch.countDown();
            }
        };
        notificationManager.registerObserver(observer);

        // 3. Mark room sanitized by cleaning staff
        long startMs = System.currentTimeMillis();
        sanitizationService.markRoomSanitized(bedId, "Staff Nurse Maria", hospital, "ICU");
        boolean received = alertLatch.await(500, TimeUnit.MILLISECONDS);
        long elapsedMs = System.currentTimeMillis() - startMs;

        notificationManager.unregisterObserver(observer);

        // SLA Performance Verification: target < 500 ms (SRS 4.1)
        assertTrue(received, "Alert delivery exceeded SLA target of 500 ms");
        assertTrue(elapsedMs < 500, "Alert SLA violated: took " + elapsedMs + " ms, expected < 500 ms");

        // Sanitization Bed State Verification
        assertEquals(BedStatus.VACANT, sanitizationService.getCurrentBedStatus(bedId),
                "Cleaned bed must transition to VACANT");
        assertNotNull(deliveredAlert.get(), "Observer must have captured the BED_CLEANED alert");
        assertEquals(SystemAlert.TYPE_BED_CLEANED, deliveredAlert.get().getAlertType());
        assertTrue(deliveredAlert.get().getMessage().contains("Staff Nurse Maria"),
                "Alert message must contain cleaning staff name for audit tracking");

        // 4. Offline Queue and Acknowledgment Testing (U6 Exception 3)
        SystemAlert offlineAlert = new SystemAlert(
                SystemAlert.TYPE_BED_CLEANED,
                UserRole.NURSE,
                hospital,
                bedId,
                "Offline Queue Bed Cleaned Alert"
        );
        SystemAlert savedAlert = alertDAO.saveAlert(offlineAlert);

        // Unacknowledged alerts must include savedAlert
        List<SystemAlert> unacked = alertDAO.fetchUnacknowledgedAlerts(hospital, UserRole.NURSE);
        assertTrue(unacked.stream().anyMatch(a -> savedAlert.getAlertId() == a.getAlertId()),
                "Offline alert must be present in unacknowledged queue");

        // Acknowledge alert
        alertDAO.markAlertAcknowledged(savedAlert.getAlertId());

        // Unacknowledged queue must no longer contain acknowledged alert
        List<SystemAlert> unackedAfter = alertDAO.fetchUnacknowledgedAlerts(hospital, UserRole.NURSE);
        assertFalse(unackedAfter.stream().anyMatch(a -> savedAlert.getAlertId() == a.getAlertId()),
                "Acknowledged alert must not appear in unacknowledged queue");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 8: Network-Wide Catastrophic Capacity Saturation (SRS F9 & U5 Exception 1)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 8: Network-Wide Capacity Saturation Broadcasts NO_CAPACITY Alert and Throws BedAlreadyBookedException")
    public void testNetworkWideCatastrophicCapacitySaturation() throws Exception {
        final String currentHospital = "Metro General Hospital";
        final long bedId = 399L;
        dispatchService.seedSimulatedBed(currentHospital, bedId, BedStatus.VACANT);

        AmbulanceReservation res = dispatchService.requestBedReservation(
                "AMB-ALS-101", TriageLevel.LEVEL_1, Set.of(EquipmentType.VENTILATOR),
                currentHospital, "SaturationPatient"
        );

        // Fill all beds across partner hospitals
        for (String partner : List.of("City Central Hospital", "Memorial Care Hospital", "St. Jude Emergency Center")) {
            for (int b = 1; b <= 600; b++) {
                dispatchService.seedSimulatedBed(partner, (long) b, BedStatus.OCCUPIED);
            }
        }

        CountDownLatch noCapLatch = new CountDownLatch(1);
        notificationManager.registerObserver(alert -> {
            if (SystemAlert.TYPE_NO_CAPACITY.equals(alert.getAlertType())) {
                noCapLatch.countDown();
            }
        });

        assertThrows(BedAlreadyBookedException.class, () ->
                dispatchService.checkAndRerouteIfFull(res.getReservationId(), true));

        boolean alertReceived = noCapLatch.await(500, TimeUnit.MILLISECONDS);
        assertTrue(alertReceived, "TYPE_NO_CAPACITY alert must be broadcast to triage consoles");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 9: In-Flight Rejection on Dispatched/Unavailable Ambulance
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 9: In-Flight Rejection on Dispatched/Unavailable Ambulance")
    public void testInFlightRejectionOnDispatchedAmbulance() {
        ambulanceDAO.updateAmbulanceAvailability("AMB-ALS-101", false, "City Central Hospital");
        try {
            assertThrows(IllegalStateException.class, () ->
                    dispatchService.requestBedReservation(
                            "AMB-ALS-101", TriageLevel.LEVEL_1, Set.of(), "City Central Hospital", "DoubleDispatchPatient"
                    ));
        } finally {
            ambulanceDAO.updateAmbulanceAvailability("AMB-ALS-101", true, "City Central Hospital");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 10: Non-Existent Ambulance ID Rejection
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 10: Non-Existent Ambulance ID Throws IllegalStateException")
    public void testNonExistentAmbulanceIdValidation() {
        assertThrows(IllegalStateException.class, () ->
                dispatchService.requestBedReservation(
                        "AMB-NONEXISTENT-999", TriageLevel.LEVEL_1, Set.of(), "City Central Hospital", "GhostPatient"
                ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 11: Terminal Invariant Guards - Defense Against Re-Admission and Reroute
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 11: Invariant Guards - Admitted Reservation Cannot Be Re-admitted or Rerouted")
    public void testTerminalInvariantGuardsAdmittedReservationDefense() {
        dispatchService.seedSimulatedBed("City Central Hospital", 114L, BedStatus.VACANT);
        AmbulanceReservation res = dispatchService.requestBedReservation(
                "AMB-ALS-101", TriageLevel.LEVEL_2, Set.of(), "City Central Hospital", "InvariantPatient"
        );
        ambulanceDAO.confirmArrivalAtomic(res.getReservationId());

        assertThrows(IllegalStateException.class, () ->
                ambulanceDAO.confirmArrivalAtomic(res.getReservationId()),
                "Re-confirming arrival must throw IllegalStateException");

        assertThrows(IllegalStateException.class, () ->
                dispatchService.checkAndRerouteIfFull(res.getReservationId(), true),
                "Rerouting already admitted reservation must throw IllegalStateException");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 12: Background Hold Expiry Daemon Sweep & Ambulance Availability Release
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 12: Hold Expiry Daemon Sweep Releases Bed to VACANT and Frees Ambulance Unit")
    public void testBackgroundHoldExpirySweepAndAmbulanceRelease() {
        dispatchService.seedSimulatedBed("City Central Hospital", 115L, BedStatus.VACANT);
        AmbulanceReservation res = dispatchService.requestBedReservation(
                "AMB-BLS-201", TriageLevel.LEVEL_3, Set.of(), "City Central Hospital", "ExpiryPatient"
        );

        LocalDateTime future = LocalDateTime.now().plusMinutes(30);
        dispatchService.runExpirySweepAt(future);

        AmbulanceReservation expiredRes = ambulanceDAO.findReservationById(res.getReservationId()).orElseThrow();
        assertEquals(AmbulanceReservation.STATUS_EXPIRED, expiredRes.getStatus());
        assertEquals(BedStatus.VACANT, ambulanceDAO.getSimulatedBedStatus(115L));

        Ambulance amb = ambulanceDAO.findAmbulanceById("AMB-BLS-201").orElseThrow();
        assertTrue(amb.isAvailable(), "Ambulance must be restored to available upon reservation expiry");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 13: Isolation Containment Safety Buffer Lock (SRS F12 & Module P3 Integration)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 13: Isolation Containment Safety Buffer Lock Blocks Pre-Arrival Holds and Patient Placement")
    public void testIsolationContainmentSyncAndBufferLock() {
        RestrictedBufferState buffer = new RestrictedBufferState();
        assertFalse(buffer.canReserveEnRoute());
        assertFalse(buffer.canAcceptPatient());
        assertThrows(IllegalBedStateException.class, buffer::reserveEnRoute);
        assertThrows(IllegalBedStateException.class, buffer::assignPatient);

        BedState released = buffer.releaseBuffer();
        assertInstanceOf(VacantState.class, released, "releaseBuffer must return VacantState");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 14: System-Wide Broadcast with Null Target Role (SRS F10 Alert Engine)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 14: System-Wide Broadcast with Null Target Role Dispatches Without NPE")
    public void testSystemWideBroadcastWithNullTargetRole() {
        SystemAlert sysAlert = new SystemAlert(
                SystemAlert.TYPE_BED_CLEANED, null, "City Central Hospital", 999L,
                "System-wide broadcast with null target role"
        );
        alertDAO.saveAlert(sysAlert);

        List<SystemAlert> alerts = alertDAO.fetchUnacknowledgedAlerts("City Central Hospital", null);
        assertTrue(alerts.stream().anyMatch(a -> Long.valueOf(999L).equals(a.getBedId())),
                "System-wide alert must be retrievable when targetRole is null");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Case 15: Paramedic Unidentified Emergency Patient Fallback
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 15: Paramedic Fallback Tag for Unidentified Critical Emergency Patients")
    public void testParamedicEmergencyPatientFallback() {
        dispatchService.seedSimulatedBed("City Central Hospital", 120L, BedStatus.VACANT);
        AmbulanceReservation res = dispatchService.requestBedReservation(
                "AMB-ALS-101", TriageLevel.LEVEL_1, Set.of(), "City Central Hospital", "   "
        );
        assertEquals("Emergency Patient (Unidentified)", res.getPatientName(),
                "Blank patient name must fall back to 'Emergency Patient (Unidentified)'");
    }
}
