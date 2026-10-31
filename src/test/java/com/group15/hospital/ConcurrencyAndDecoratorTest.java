package com.group15.hospital;

import com.group15.hospital.exception.EquipmentLimitExceededException;
import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.bed.*;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests covering:
 * <ol>
 *   <li><strong>Decorator Pattern</strong> — cost accumulation, description chaining,
 *       equipment lists, duplicate/limit guards, and state delegation.</li>
 *   <li><strong>MedicalMachine</strong> — operating hour tracking, safe-hour enforcement,
 *       and maintenance lifecycle.</li>
 *   <li><strong>WaitlistEntry</strong> — priority ordering semantics.</li>
 *   <li><strong>Concurrency</strong> — multi-threaded bed booking races and
 *       concurrent decorator reads.</li>
 * </ol>
 *
 * @author Group 15
 */
class ConcurrencyAndDecoratorTest {

    // ═══════════════════════════════════════════════════════════════════════
    //  1. Decorator Pattern Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Decorator Pattern Tests")
    class DecoratorPatternTests {

        @Test
        @DisplayName("Base bed has correct default values")
        void testBaseBedDefaultValues() {
            BaseBed bed = new BaseBed(1L, "ICU-A", "City Hospital");

            assertEquals(1L, bed.getBedId());
            assertEquals("ICU-A", bed.getWardName());
            assertEquals("City Hospital", bed.getHospitalName());
            assertEquals(BedStatus.VACANT, bed.getBedStatus());
            assertEquals(BaseBed.BASE_HOURLY_COST, bed.getHourlyCost(), 0.001);
            assertTrue(bed.getAttachedEquipment().isEmpty(),
                    "Base bed should have no equipment");
            assertNull(bed.getCurrentPatientId());
            assertNull(bed.getCurrentPatientName());
            assertNull(bed.getCurrentTriageLevel());
        }

        @Test
        @DisplayName("Single decorator (Ventilator) adds correct cost and equipment")
        void testSingleDecoratorVentilator() {
            BedComponent bed = new VentilatorDecorator(
                    new BaseBed(2L, "ICU-B", "Metro Hospital"));

            // Base $50 + Ventilator $150 = $200
            assertEquals(200.0, bed.getHourlyCost(), 0.001);
            assertTrue(bed.getDescription().contains("Ventilator"));
            assertEquals(1, bed.getAttachedEquipment().size());
            assertTrue(bed.getAttachedEquipment().contains(EquipmentType.VENTILATOR));
        }

        @Test
        @DisplayName("Double decorator stacking (Ventilator + Oxygen)")
        void testDoubleDecoratorStacking() {
            BedComponent bed = new OxygenCylinderDecorator(
                    new VentilatorDecorator(
                            new BaseBed(3L, "ICU-C", "Central Hospital")));

            // Base $50 + Ventilator $150 + Oxygen $40 = $240
            assertEquals(240.0, bed.getHourlyCost(), 0.001);
            assertEquals(2, bed.getAttachedEquipment().size());
            assertTrue(bed.getAttachedEquipment().contains(EquipmentType.VENTILATOR));
            assertTrue(bed.getAttachedEquipment().contains(EquipmentType.OXYGEN));
        }

        @Test
        @DisplayName("Triple decorator stacking (Ventilator + Oxygen + Monitor)")
        void testTripleDecoratorStacking() {
            BedComponent bed = new CardiacMonitorDecorator(
                    new OxygenCylinderDecorator(
                            new VentilatorDecorator(
                                    new BaseBed(4L, "ICU-D", "General Hospital"))));

            // Base $50 + Ventilator $150 + Oxygen $40 + Monitor $60 = $300
            assertEquals(300.0, bed.getHourlyCost(), 0.001);
            assertEquals(3, bed.getAttachedEquipment().size());
            assertTrue(bed.getDescription().contains("Ventilator"));
            assertTrue(bed.getDescription().contains("Oxygen"));
            assertTrue(bed.getDescription().contains("Cardiac Monitor"));
        }

        @Test
        @DisplayName("Full decorator stack — all 4 equipment types")
        void testFullDecoratorStack() {
            BedComponent bed = new CardiacMonitorDecorator(
                    new DialysisDecorator(
                            new OxygenCylinderDecorator(
                                    new VentilatorDecorator(
                                            new BaseBed(5L, "ICU-E", "University Hospital")))));

            // Base $50 + Ventilator $150 + Oxygen $40 + Dialysis $200 + Monitor $60 = $500
            assertEquals(500.0, bed.getHourlyCost(), 0.001);
            assertEquals(4, bed.getAttachedEquipment().size());
            assertTrue(bed.getAttachedEquipment().contains(EquipmentType.VENTILATOR));
            assertTrue(bed.getAttachedEquipment().contains(EquipmentType.OXYGEN));
            assertTrue(bed.getAttachedEquipment().contains(EquipmentType.DIALYSIS));
            assertTrue(bed.getAttachedEquipment().contains(EquipmentType.MONITOR));
        }

        @Test
        @DisplayName("Duplicate decorator throws EquipmentLimitExceededException")
        void testDuplicateDecoratorThrowsException() {
            BedComponent bedWithVentilator = new VentilatorDecorator(
                    new BaseBed(6L, "ICU-F", "Regional Hospital"));

            EquipmentLimitExceededException ex = assertThrows(
                    EquipmentLimitExceededException.class,
                    () -> new VentilatorDecorator(bedWithVentilator),
                    "Adding a duplicate Ventilator should throw");

            assertTrue(ex.getMessage().contains("already has"),
                    "Exception message should mention duplicate");
        }

        @Test
        @DisplayName("Exceeding max equipment limit throws EquipmentLimitExceededException")
        void testExceedMaxEquipmentLimit() {
            // All 4 types are already attached — attempting a duplicate (5th) must fail
            BedComponent fullBed = new CardiacMonitorDecorator(
                    new DialysisDecorator(
                            new OxygenCylinderDecorator(
                                    new VentilatorDecorator(
                                            new BaseBed(7L, "ICU-G", "County Hospital")))));

            assertEquals(4, fullBed.getAttachedEquipment().size());
            assertEquals(BedEquipmentDecorator.MAX_EQUIPMENT_PER_BED,
                    fullBed.getAttachedEquipment().size(),
                    "Full bed should have exactly MAX_EQUIPMENT_PER_BED items");

            // Any additional decorator should fail (duplicate check catches it first,
            // but the max limit guard would also catch a hypothetical 5th unique type)
            assertThrows(EquipmentLimitExceededException.class,
                    () -> new VentilatorDecorator(fullBed),
                    "Adding to a fully-equipped bed should throw");
        }

        @Test
        @DisplayName("Decorator preserves patient state through delegation")
        void testDecoratorPreservesPatientState() {
            BaseBed base = new BaseBed(8L, "ICU-H", "State Hospital");
            base.assignPatient(100L, "John Doe", TriageLevel.LEVEL_2);

            // Decorate AFTER patient assignment
            BedComponent decorated = new VentilatorDecorator(base);

            assertEquals(100L, decorated.getCurrentPatientId());
            assertEquals("John Doe", decorated.getCurrentPatientName());
            assertEquals(TriageLevel.LEVEL_2, decorated.getCurrentTriageLevel());
            assertEquals(BedStatus.OCCUPIED, decorated.getBedStatus());
        }

        @Test
        @DisplayName("Bed status changes flow through decorators")
        void testDecoratorPreservesBedStatus() {
            BaseBed base = new BaseBed(9L, "ICU-I", "Private Hospital");
            BedComponent decorated = new OxygenCylinderDecorator(
                    new VentilatorDecorator(base));

            assertEquals(BedStatus.VACANT, decorated.getBedStatus());

            // Change status via the decorated reference
            decorated.setBedStatus(BedStatus.RESERVED_EN_ROUTE);
            assertEquals(BedStatus.RESERVED_EN_ROUTE, decorated.getBedStatus());
            assertEquals(BedStatus.RESERVED_EN_ROUTE, base.getBedStatus(),
                    "Status change should flow down to the base bed");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2. MedicalMachine Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MedicalMachine Tests")
    class MedicalMachineTests {

        @Test
        @DisplayName("Operating hours tracking — add and check remaining")
        void testMachineOperatingHoursTracking() {
            MedicalMachine machine = new MedicalMachine(
                    1L, EquipmentType.VENTILATOR, "VENT-001", "City Hospital");

            assertEquals(0.0, machine.getOperatingHoursUsed(), 0.001);
            assertEquals(500.0, machine.getRemainingHours(), 0.001);
            assertTrue(machine.isWithinSafeOperatingHours());

            machine.addOperatingHours(100.0);
            assertEquals(100.0, machine.getOperatingHoursUsed(), 0.001);
            assertEquals(400.0, machine.getRemainingHours(), 0.001);

            machine.addOperatingHours(350.0);
            assertEquals(450.0, machine.getOperatingHoursUsed(), 0.001);
            assertEquals(50.0, machine.getRemainingHours(), 0.001);
            assertEquals(90.0, machine.getUsagePercentage(), 0.001);
        }

        @Test
        @DisplayName("Exceeding safe operating hours throws exception")
        void testMachineExceedsSafeHoursThrowsException() {
            MedicalMachine machine = new MedicalMachine(
                    2L, EquipmentType.DIALYSIS, "DIA-001", "Metro Hospital");

            // Dialysis safe limit = 300 hours
            machine.addOperatingHours(250.0);

            assertThrows(EquipmentLimitExceededException.class,
                    () -> machine.addOperatingHours(60.0),
                    "Adding 60h to 250h should exceed the 300h limit");
        }

        @Test
        @DisplayName("completeMaintenance resets hours to zero")
        void testMachineMaintenanceReset() {
            MedicalMachine machine = new MedicalMachine(
                    3L, EquipmentType.OXYGEN, "OXY-001", "General Hospital",
                    800.0, true);

            assertTrue(machine.isUnderMaintenance());
            assertEquals(800.0, machine.getOperatingHoursUsed(), 0.001);

            machine.completeMaintenance();

            assertFalse(machine.isUnderMaintenance());
            assertEquals(0.0, machine.getOperatingHoursUsed(), 0.001);
            assertTrue(machine.isWithinSafeOperatingHours());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3. WaitlistEntry Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WaitlistEntry Tests")
    class WaitlistEntryTests {

        @Test
        @DisplayName("Priority ordering — LEVEL_1 before LEVEL_5, FIFO among same level")
        void testWaitlistPriorityOrdering() {
            LocalDateTime now = LocalDateTime.now();

            WaitlistEntry critical = new WaitlistEntry(
                    1L, 101L, "Critical Patient", TriageLevel.LEVEL_1,
                    "City Hospital", "ICU-A", now.minusMinutes(5), "");

            WaitlistEntry nonUrgent = new WaitlistEntry(
                    2L, 102L, "Non-Urgent Patient", TriageLevel.LEVEL_5,
                    "City Hospital", "ICU-A", now.minusMinutes(10), "");

            WaitlistEntry anotherCritical = new WaitlistEntry(
                    3L, 103L, "Another Critical", TriageLevel.LEVEL_1,
                    "City Hospital", "ICU-A", now, "");

            List<WaitlistEntry> list = new ArrayList<>();
            list.add(nonUrgent);
            list.add(anotherCritical);
            list.add(critical);

            Collections.sort(list);

            assertEquals("Critical Patient", list.get(0).getPatientName(),
                    "LEVEL_1 patient (earlier time) should come first");
            assertEquals("Another Critical", list.get(1).getPatientName(),
                    "LEVEL_1 patient (later time) should come second");
            assertEquals("Non-Urgent Patient", list.get(2).getPatientName(),
                    "LEVEL_5 patient should come last");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  4. Concurrency Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent bed booking — exactly 1 thread succeeds out of 10")
        void testConcurrentBedBookingRaceCondition() throws InterruptedException {
            BaseBed bed = new BaseBed(100L, "ICU-RACE", "Test Hospital");
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int patientIdx = i;
                executor.submit(() -> {
                    try {
                        startGate.await(); // All threads start simultaneously
                        bed.assignPatient(patientIdx, "Patient-" + patientIdx, TriageLevel.LEVEL_3);
                        successCount.incrementAndGet();
                    } catch (IllegalBedStateException e) {
                        failureCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Release all threads at once
            startGate.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Exactly 1 should succeed (first to execute), rest should fail
            assertEquals(1, successCount.get(),
                    "Exactly one thread should successfully assign the patient");
            assertEquals(threadCount - 1, failureCount.get(),
                    "All other threads should get IllegalBedStateException");
            assertEquals(BedStatus.OCCUPIED, bed.getBedStatus());
            assertNotNull(bed.getCurrentPatientName(),
                    "Bed should have exactly one patient assigned");
        }

        @Test
        @DisplayName("Concurrent read access on decorated bed returns consistent values")
        void testConcurrentDecoratorAccess() throws InterruptedException {
            BedComponent decorated = new CardiacMonitorDecorator(
                    new DialysisDecorator(
                            new OxygenCylinderDecorator(
                                    new VentilatorDecorator(
                                            new BaseBed(200L, "ICU-CONCURRENT", "Parallel Hospital")))));

            final double expectedCost = 500.0;
            final int expectedEquipmentCount = 4;
            int threadCount = 10;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    // Each thread reads cost and equipment concurrently
                    double cost = decorated.getHourlyCost();
                    List<EquipmentType> equipment = decorated.getAttachedEquipment();
                    String description = decorated.getDescription();

                    return Math.abs(cost - expectedCost) < 0.001
                            && equipment.size() == expectedEquipmentCount
                            && description.contains("Ventilator")
                            && description.contains("Dialysis");
                }));
            }

            startGate.countDown();

            for (Future<Boolean> future : futures) {
                try {
                    assertTrue(future.get(5, TimeUnit.SECONDS),
                            "Concurrent read should return consistent values");
                } catch (ExecutionException | TimeoutException e) {
                    fail("Concurrent read failed: " + e.getMessage());
                }
            }

            executor.shutdown();
        }
    }
}
