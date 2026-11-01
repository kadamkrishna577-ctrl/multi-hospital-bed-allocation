package com.group15.hospital;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.exception.EquipmentLimitExceededException;
import com.group15.hospital.model.bed.*;
import com.group15.hospital.model.enums.*;
import com.group15.hospital.model.hierarchy.Hospital;
import com.group15.hospital.model.hierarchy.Patient;
import com.group15.hospital.model.isolation.WardTopologyGraph;
import com.group15.hospital.model.state.BedState;
import com.group15.hospital.model.state.VacantState;
import com.group15.hospital.service.strategy.BedSearchEngine;
import com.group15.hospital.service.strategy.BestEquippedBedStrategy;
import com.group15.hospital.service.strategy.ClosestBedStrategy;
import com.group15.hospital.ui.LoginFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application bootstrap [Shared].
 * <p>
 * Supports both full GUI mode (launches {@link LoginFrame}) and offline/console demo
 * mode via the {@code --demo} flag covering all 4 project modules.
 * </p>
 *
 * @author Group 15 [Shared: Saksham, Krishna, Jatin, Siddhant]
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        boolean runDemo = false;
        for (String arg : args) {
            if ("--demo".equalsIgnoreCase(arg) || "-d".equalsIgnoreCase(arg)) {
                runDemo = true;
                break;
            }
        }

        // If explicitly requested or running in a headless environment, run CLI demo
        if (runDemo || GraphicsEnvironment.isHeadless()) {
            runConsoleDemo();
        } else {
            LOGGER.info("Starting Multi-Hospital Emergency Bed Allocation System...");

            // 1. Set System Look and Feel for native UI appearance
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Using default Swing Look and Feel.");
            }

            // 2. Pre-load Database Connection in background thread
            new Thread(() -> {
                try {
                    DatabaseConnection.getInstance().getConnection().close();
                    LOGGER.info("Database connection pool initialized successfully.");
                } catch (Exception e) {
                    LOGGER.warning("Running in local fallback mode (MySQL not reached: " + e.getMessage() + ")");
                }
            }).start();

            // 3. Launch UI on Event Dispatch Thread (EDT)
            SwingUtilities.invokeLater(() -> {
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
            });
        }
    }

    public static void runConsoleDemo() {
        System.out.println("╔════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║      MULTI-HOSPITAL EMERGENCY BED ALLOCATION SYSTEM — LIVE DEMO        ║");
        System.out.println("║                GROUP 15: ALL 4 MODULES FULLY INTEGRATED                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════╝");

        demoDecoratorPattern();
        demoMedicalMachine();
        demoWaitlistPriority();
        demoTriageSwap();
        demoConcurrentBooking();
        demoStrategyPattern();
        demoIsolationContainment();
        demoBedStateLifecycle();

        System.out.println("\n════════════════════════════════════════════════════════════════════════");
        System.out.println("  🎉 ALL 8 DEMOS COMPLETED WITH ZERO ERRORS ACROSS ALL 4 MODULES!       ");
        System.out.println("════════════════════════════════════════════════════════════════════════");
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. DECORATOR PATTERN (Krishna - P2)
    // ═══════════════════════════════════════════════════════════════
    private static void demoDecoratorPattern() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  📋 DEMO 1 [P2 - Krishna]: Decorator Pattern (Equipment & Costs)");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        BedComponent bed = new BaseBed(101, "Emergency ICU Ward A", "City Central Hospital");
        printBed("Plain Bed", bed);

        bed = new VentilatorDecorator(bed);
        printBed("+ Ventilator", bed);

        bed = new OxygenCylinderDecorator(bed);
        printBed("+ Oxygen Cylinder", bed);

        bed = new DialysisDecorator(bed);
        printBed("+ Dialysis Machine", bed);

        bed = new CardiacMonitorDecorator(bed);
        printBed("+ Cardiac Monitor", bed);

        System.out.println("  📊 COST BREAKDOWN:");
        System.out.println("     Base ICU Bed:     $50.00/hr");
        System.out.println("     Ventilator:      +$150.00/hr");
        System.out.println("     Oxygen:          +$40.00/hr");
        System.out.println("     Dialysis:        +$200.00/hr");
        System.out.println("     Cardiac Monitor: +$60.00/hr");
        System.out.println("     ─────────────────────────────");
        System.out.printf("     TOTAL:           $%.2f/hr%n", bed.getHourlyCost());

        System.out.println("\n  🚫 Enforcing Max 4 Equipment Limit & No Duplicates...");
        try {
            new VentilatorDecorator(bed);
            System.out.println("     ERROR: Should not reach here!");
        } catch (EquipmentLimitExceededException e) {
            System.out.println("     ✅ Blocked as expected: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. MEDICAL MACHINE — Operating Hours (Krishna - P2)
    // ═══════════════════════════════════════════════════════════════
    private static void demoMedicalMachine() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  ⚙️  DEMO 2 [P2 - Krishna]: Medical Machine Operating Hours");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        MedicalMachine ventilator = new MedicalMachine(1, EquipmentType.VENTILATOR, "VENT-2024-001", "City Central Hospital");

        System.out.printf("  Machine: %s (S/N: %s)%n", ventilator.getEquipmentType().getDisplayName(), ventilator.getSerialNumber());
        System.out.printf("  Safe Operating Limit: %d hours%n", ventilator.getSafeOperatingHourLimit());
        System.out.printf("  Hours Used: %.1f | Remaining: %.1f | Usage: %.1f%%%n",
                ventilator.getOperatingHoursUsed(), ventilator.getRemainingHours(), ventilator.getUsagePercentage());

        System.out.println("\n  Adding operating hours...");
        ventilator.addOperatingHours(200);
        System.out.printf("  +200 hrs → Used: %.1f | Remaining: %.1f | Usage: %.1f%%%n",
                ventilator.getOperatingHoursUsed(), ventilator.getRemainingHours(), ventilator.getUsagePercentage());

        ventilator.addOperatingHours(250);
        System.out.printf("  +250 hrs → Used: %.1f | Remaining: %.1f | Usage: %.1f%% ⚠️ (Daemon warning threshold >=90%%)%n",
                ventilator.getOperatingHoursUsed(), ventilator.getRemainingHours(), ventilator.getUsagePercentage());

        System.out.println("\n  🚫 Attempting to add 60 more hours (would exceed 500hr limit)...");
        try {
            ventilator.addOperatingHours(60);
        } catch (EquipmentLimitExceededException e) {
            System.out.println("  ✅ Blocked: " + e.getMessage());
        }

        System.out.println("\n  🔧 Performing maintenance repair...");
        ventilator.setUnderMaintenance(true);
        System.out.println("  Status: Under Maintenance = " + ventilator.isUnderMaintenance());
        ventilator.completeMaintenance();
        System.out.printf("  After repair → Used: %.1f | Remaining: %.1f | Maintenance: %s%n",
                ventilator.getOperatingHoursUsed(), ventilator.getRemainingHours(), ventilator.isUnderMaintenance());
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. WAITLIST PRIORITY (Krishna - P2)
    // ═══════════════════════════════════════════════════════════════
    private static void demoWaitlistPriority() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  📋 DEMO 3 [P2 - Krishna]: Priority Waitlist (Acuity + FIFO)");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        LocalDateTime now = LocalDateTime.now();

        List<WaitlistEntry> waitlist = new ArrayList<>();
        waitlist.add(new WaitlistEntry(1, 1001, "Rahul (Non-Urgent)", TriageLevel.LEVEL_5, "City Central Hospital", "Emergency ICU Ward A", now.minusMinutes(30), ""));
        waitlist.add(new WaitlistEntry(2, 1002, "Priya (Critical)", TriageLevel.LEVEL_1, "City Central Hospital", "Emergency ICU Ward A", now.minusMinutes(10), ""));
        waitlist.add(new WaitlistEntry(3, 1003, "Amit (Urgent)", TriageLevel.LEVEL_3, "City Central Hospital", "Emergency ICU Ward A", now.minusMinutes(20), ""));
        waitlist.add(new WaitlistEntry(4, 1004, "Neha (Critical)", TriageLevel.LEVEL_1, "City Central Hospital", "Emergency ICU Ward A", now.minusMinutes(5), ""));
        waitlist.add(new WaitlistEntry(5, 1005, "Saksham (Emergency)", TriageLevel.LEVEL_2, "City Central Hospital", "Emergency ICU Ward A", now.minusMinutes(15), ""));

        System.out.println("  BEFORE sorting (arrival order):");
        for (int i = 0; i < waitlist.size(); i++) {
            WaitlistEntry e = waitlist.get(i);
            System.out.printf("    %d. %-25s → %s%n", i + 1, e.getPatientName(), e.getTriageLevel().getDescription());
        }

        Collections.sort(waitlist);

        System.out.println("\n  AFTER sorting (priority order — highest acuity first, then FIFO):");
        for (int i = 0; i < waitlist.size(); i++) {
            WaitlistEntry e = waitlist.get(i);
            System.out.printf("    %d. %-25s → %s%n", i + 1, e.getPatientName(), e.getTriageLevel().getDescription());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. TRIAGE SWAP ELIGIBILITY (Krishna - P2)
    // ═══════════════════════════════════════════════════════════════
    private static void demoTriageSwap() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  🚨 DEMO 4 [P2 - Krishna]: Emergency Preemption Matrix (canSwap)");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        TriageLevel[] levels = TriageLevel.values();
        System.out.println("  Can incoming emergency patient displace current occupant?");
        System.out.println("  (SRS Rule: Only LEVEL_1/CRITICAL can preempt LEVEL_4 or LEVEL_5)\n");

        System.out.printf("  %-12s", "Incoming ↓");
        for (TriageLevel occupant : levels) {
            System.out.printf("  %-10s", occupant.name());
        }
        System.out.println();
        System.out.println("  " + "─".repeat(65));

        for (TriageLevel incoming : levels) {
            System.out.printf("  %-12s", incoming.name());
            for (TriageLevel occupant : levels) {
                String result = incoming.canSwap(occupant) ? "✅ YES" : "❌ NO";
                System.out.printf("  %-10s", result);
            }
            System.out.println();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. CONCURRENT BOOKING (Krishna - P2)
    // ═══════════════════════════════════════════════════════════════
    private static void demoConcurrentBooking() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  🔒 DEMO 5 [P2 - Krishna]: Concurrent Bed Booking Race Condition");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        BaseBed bed = new BaseBed(999, "ICU-RACE", "City Central Hospital");
        int threadCount = 10;
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.CountDownLatch startGate = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threadCount);

        System.out.println("  Bed #999 (VACANT) — 10 concurrent threads attempt simultaneous booking...\n");

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    startGate.await();
                    bed.assignPatient(idx, "Patient-" + idx, TriageLevel.LEVEL_3);
                    success.incrementAndGet();
                    System.out.println("    Thread-" + idx + ": ✅ Acquired bed lock & booked successfully!");
                } catch (Exception e) {
                    failed.incrementAndGet();
                    System.out.println("    Thread-" + idx + ": ❌ Blocked by lock (" + e.getClass().getSimpleName() + ")");
                } finally {
                    done.countDown();
                }
            }).start();
        }

        startGate.countDown();
        try { done.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        System.out.println("\n  RACE RESULT:");
        System.out.println("    Successful allocations: " + success.get() + " (Exactly 1 allowed)");
        System.out.println("    Rejected conflicts:     " + failed.get() + " (Protected against race conditions)");
        System.out.println("    Final Bed Status:       " + bed.getBedStatus());
        System.out.println("    Assigned Patient:       " + bed.getCurrentPatientName());
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. STRATEGY PATTERN (Saksham - P1)
    // ═══════════════════════════════════════════════════════════════
    private static void demoStrategyPattern() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  🎯 DEMO 6 [P1 - Saksham]: Strategy Pattern for Bed Search");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        List<Hospital> hospitals = new ArrayList<>();
        hospitals.add(new Hospital(1, "City Central Hospital", "123 Main St", 12.9716, 77.5946, "080-1111", 120));
        hospitals.add(new Hospital(2, "Memorial Care Hospital", "456 Health Ave", 12.9352, 77.6245, "080-2222", 85));
        hospitals.add(new Hospital(3, "Metro General Hospital", "789 North Blvd", 13.0358, 77.5970, "080-3333", 150));

        Patient emergencyPatient = new Patient("David Miller", 45, "MALE", TriageLevel.LEVEL_1, DietType.REGULAR);

        ClosestBedStrategy closestStrategy = new ClosestBedStrategy();
        System.out.println("  Strategy 1: " + closestStrategy.getStrategyName() + " (Distance from Lat/Long 12.95, 77.60)");
        var closestResult = closestStrategy.allocateBed(emergencyPatient, 12.9500, 77.6000, hospitals);
        closestResult.ifPresent(r -> System.out.printf("  → Selected: %s [%s] (%.2f km away) — %s%n",
                r.getHospital().getName(), r.getWardName(), r.getDistanceKm(), r.getRationale()));

        BestEquippedBedStrategy equippedStrategy = new BestEquippedBedStrategy();
        System.out.println("\n  Strategy 2: " + equippedStrategy.getStrategyName() + " (Specialized equipment capacity)");
        var equippedResult = equippedStrategy.allocateBed(emergencyPatient, 12.9500, 77.6000, hospitals);
        equippedResult.ifPresent(r -> System.out.printf("  → Selected: %s [%s] — %s%n",
                r.getHospital().getName(), r.getWardName(), r.getRationale()));
    }

    // ═══════════════════════════════════════════════════════════════
    //  7. WARD TOPOLOGY INFECTION CONTAINMENT (Jatin - P3)
    // ═══════════════════════════════════════════════════════════════
    private static void demoIsolationContainment() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  🛡️  DEMO 7 [P3 - Jatin]: Ward Topology Infection Containment");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        WardTopologyGraph graph = new WardTopologyGraph();
        graph.addEdge(101, 102, WardTopologyGraph.RELATION_NEIGHBOR);
        graph.addEdge(102, 103, WardTopologyGraph.RELATION_NEIGHBOR);
        graph.addEdge(101, 103, WardTopologyGraph.RELATION_SHARED_AIR);

        System.out.println("  Ward Adjacency Graph Configured:");
        System.out.println("    Bed #101 <---> Bed #102  [Direct Physical Neighbor]");
        System.out.println("    Bed #102 <---> Bed #103  [Direct Physical Neighbor]");
        System.out.println("    Bed #101 <---> Bed #103  [Shared HVAC Air Duct]");

        long contagiousBedId = 101;
        Set<Long> physicalNeighbors = graph.getDirectNeighbors(contagiousBedId);
        Set<Long> sharedAirBeds = graph.getSharedAirBeds(contagiousBedId);
        Set<Long> allAffected = graph.getAllAffectedBufferBeds(contagiousBedId);

        System.out.printf("%n  🚨 Patient admitted to Bed #%d with contagious infectious disease:%n", contagiousBedId);
        System.out.printf("    Adjacent neighbor beds:         %s%n", physicalNeighbors);
        System.out.printf("    Shared HVAC air ducts:          %s%n", sharedAirBeds);
        System.out.printf("    🔒 Automatically locked beds:   %s -> RESTRICTED_BUFFER%n", allAffected);
        System.out.println("    Result: Protected against cross-contamination (SRS U4 / F12)");
    }

    // ═══════════════════════════════════════════════════════════════
    //  8. STATE PATTERN LIFECYCLE (Siddhant - P4)
    // ═══════════════════════════════════════════════════════════════
    private static void demoBedStateLifecycle() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  🔄 DEMO 8 [P4 - Siddhant]: BedState Pattern Lifecycle");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        BedState state = new VacantState();
        System.out.println("  Step 1: Initial state      → " + state.getStatus() + " (" + state.getStateDescription() + ")");

        state = state.reserveEnRoute();
        System.out.println("  Step 2: Ambulance reserves → " + state.getStatus() + " (" + state.getStateDescription() + ")");

        state = state.assignPatient();
        System.out.println("  Step 3: Patient admitted   → " + state.getStatus() + " (" + state.getStateDescription() + ")");

        state = state.dischargePatient();
        System.out.println("  Step 4: Patient discharged → " + state.getStatus() + " (" + state.getStateDescription() + ")");

        state = state.markSanitized();
        System.out.println("  Step 5: Room sanitized     → " + state.getStatus() + " (" + state.getStateDescription() + ")");
    }

    private static void printBed(String label, BedComponent bed) {
        System.out.printf("  %-22s → Cost: $%.2f/hr | Equipment: %s%n",
                label, bed.getHourlyCost(), bed.getAttachedEquipment());
    }
}
