package com.group15.hospital;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.exception.EquipmentLimitExceededException;
import com.group15.hospital.model.bed.*;
import com.group15.hospital.model.enums.*;
import com.group15.hospital.ui.LoginFrame;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application bootstrap [Shared].
 * <p>
 * Supports both full GUI mode (launches {@link LoginFrame}) and offline/console demo
 * mode via the {@code --demo} flag.
 * </p>
 *
 * @author Group 15 [Shared: Saksham, Krishna, Siddhant]
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
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║     MULTI-HOSPITAL BED ALLOCATION SYSTEM — DEMO OUTPUT      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        demoDecoratorPattern();
        demoMedicalMachine();
        demoWaitlistPriority();
        demoTriageSwap();
        demoConcurrentBooking();

        System.out.println("\n✅ All demos completed successfully!");
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. DECORATOR PATTERN
    // ═══════════════════════════════════════════════════════════════
    private static void demoDecoratorPattern() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  📋 DEMO 1: Decorator Pattern — Stacking Equipment on Beds");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        BedComponent bed = new BaseBed(101, "ICU-A", "City Hospital");
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

        System.out.println("\n  🚫 Attempting duplicate Ventilator...");
        try {
            new VentilatorDecorator(bed);
            System.out.println("     ERROR: Should not reach here!");
        } catch (EquipmentLimitExceededException e) {
            System.out.println("     ✅ Blocked: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. MEDICAL MACHINE — Operating Hours
    // ═══════════════════════════════════════════════════════════════
    private static void demoMedicalMachine() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  ⚙️  DEMO 2: Medical Machine — Operating Hours Tracking");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        MedicalMachine ventilator = new MedicalMachine(1, EquipmentType.VENTILATOR, "VENT-2024-001", "City Hospital");

        System.out.printf("  Machine: %s (S/N: %s)%n", ventilator.getEquipmentType().getDisplayName(), ventilator.getSerialNumber());
        System.out.printf("  Safe Operating Limit: %d hours%n", ventilator.getSafeOperatingHourLimit());
        System.out.printf("  Hours Used: %.1f | Remaining: %.1f | Usage: %.1f%%%n",
                ventilator.getOperatingHoursUsed(), ventilator.getRemainingHours(), ventilator.getUsagePercentage());

        System.out.println("\n  Adding operating hours...");
        ventilator.addOperatingHours(200);
        System.out.printf("  +200 hrs → Used: %.1f | Remaining: %.1f | Usage: %.1f%%%n",
                ventilator.getOperatingHoursUsed(), ventilator.getRemainingHours(), ventilator.getUsagePercentage());

        ventilator.addOperatingHours(250);
        System.out.printf("  +250 hrs → Used: %.1f | Remaining: %.1f | Usage: %.1f%% ⚠️ WARNING%n",
                ventilator.getOperatingHoursUsed(), ventilator.getRemainingHours(), ventilator.getUsagePercentage());

        System.out.println("\n  🚫 Attempting to add 60 more hours (would exceed 500hr limit)...");
        try {
            ventilator.addOperatingHours(60);
        } catch (EquipmentLimitExceededException e) {
            System.out.println("  ✅ Blocked: " + e.getMessage());
        }

        System.out.println("\n  🔧 Performing maintenance...");
        ventilator.setUnderMaintenance(true);
        System.out.println("  Status: Under Maintenance = " + ventilator.isUnderMaintenance());
        ventilator.completeMaintenance();
        System.out.printf("  After repair → Used: %.1f | Remaining: %.1f | Maintenance: %s%n",
                ventilator.getOperatingHoursUsed(), ventilator.getRemainingHours(), ventilator.isUnderMaintenance());
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. WAITLIST PRIORITY
    // ═══════════════════════════════════════════════════════════════
    private static void demoWaitlistPriority() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  📋 DEMO 3: Waitlist Priority Ordering");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        LocalDateTime now = LocalDateTime.now();

        List<WaitlistEntry> waitlist = new ArrayList<>();
        waitlist.add(new WaitlistEntry(1, 1001, "Rahul (Non-Urgent)", TriageLevel.LEVEL_5, "City Hospital", "Emergency ICU Ward A", now.minusMinutes(30), ""));
        waitlist.add(new WaitlistEntry(2, 1002, "Priya (Critical)", TriageLevel.LEVEL_1, "City Hospital", "Emergency ICU Ward A", now.minusMinutes(10), ""));
        waitlist.add(new WaitlistEntry(3, 1003, "Amit (Urgent)", TriageLevel.LEVEL_3, "City Hospital", "Emergency ICU Ward A", now.minusMinutes(20), ""));
        waitlist.add(new WaitlistEntry(4, 1004, "Neha (Critical)", TriageLevel.LEVEL_1, "City Hospital", "Emergency ICU Ward A", now.minusMinutes(5), ""));
        waitlist.add(new WaitlistEntry(5, 1005, "Saksham (Emergency)", TriageLevel.LEVEL_2, "City Hospital", "Emergency ICU Ward A", now.minusMinutes(15), ""));

        System.out.println("  BEFORE sorting (insertion order):");
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
    //  4. TRIAGE SWAP ELIGIBILITY
    // ═══════════════════════════════════════════════════════════════
    private static void demoTriageSwap() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  🚨 DEMO 4: Emergency Pre-emption Eligibility (canSwap)");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        TriageLevel[] levels = TriageLevel.values();
        System.out.println("  Can incoming patient SWAP with current occupant?");
        System.out.println("  (Only LEVEL_1/CRITICAL can preempt LEVEL_4 or LEVEL_5)\n");

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
    //  5. CONCURRENT BOOKING
    // ═══════════════════════════════════════════════════════════════
    private static void demoConcurrentBooking() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  🔒 DEMO 5: Concurrent Bed Booking (10 threads race)");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        BaseBed bed = new BaseBed(999, "ICU-RACE", "Test Hospital");
        int threadCount = 10;
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.CountDownLatch startGate = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threadCount);

        System.out.println("  Bed #999 (VACANT) — 10 threads trying to book simultaneously...\n");

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    startGate.await();
                    bed.assignPatient(idx, "Patient-" + idx, TriageLevel.LEVEL_3);
                    success.incrementAndGet();
                    System.out.println("    Thread-" + idx + ": ✅ Booked successfully!");
                } catch (Exception e) {
                    failed.incrementAndGet();
                    System.out.println("    Thread-" + idx + ": ❌ " + e.getMessage());
                } finally {
                    done.countDown();
                }
            }).start();
        }

        startGate.countDown();
        try { done.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        System.out.println("\n  RESULT:");
        System.out.println("    Succeeded: " + success.get());
        System.out.println("    Failed:    " + failed.get());
        System.out.println("    Bed Status: " + bed.getBedStatus());
        System.out.println("    Assigned to: " + bed.getCurrentPatientName());
    }

    private static void printBed(String label, BedComponent bed) {
        System.out.printf("  %-22s → Cost: $%.2f/hr | Equipment: %s%n",
                label, bed.getHourlyCost(), bed.getAttachedEquipment());
    }
}
