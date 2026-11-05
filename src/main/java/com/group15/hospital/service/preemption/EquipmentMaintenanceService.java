package com.group15.hospital.service.preemption;

import com.group15.hospital.dao.EquipmentDAO;
import com.group15.hospital.exception.EquipmentLimitExceededException;
import com.group15.hospital.model.bed.MedicalMachine;
import com.group15.hospital.model.hierarchy.AuditLogEntry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background daemon service that periodically scans all medical equipment
 * for operating-hour compliance and flags machines that need maintenance.
 * <p>
 * The daemon runs on a single-threaded {@link ScheduledExecutorService}
 * with a configurable scan interval. Manual scans and repairs can also
 * be triggered on-demand.
 * </p>
 *
 * <h3>Scan Thresholds</h3>
 * <ul>
 *   <li><strong>Warning</strong> — usage ≥ 90% of safe operating limit</li>
 *   <li><strong>Critical</strong> — usage ≥ 100% → machine flagged for maintenance</li>
 * </ul>
 *
 * @author Group 15
 * @see MedicalMachine
 * @see EquipmentDAO
 */
public class EquipmentMaintenanceService {

    private static final Logger LOGGER = Logger.getLogger(EquipmentMaintenanceService.class.getName());

    /** Usage percentage at which a warning is logged. */
    private static final double WARNING_THRESHOLD = 90.0;

    /** Usage percentage at which the machine is flagged for maintenance. */
    private static final double CRITICAL_THRESHOLD = 100.0;

    /** Default scan interval in minutes. */
    public static final long DEFAULT_INTERVAL_MINUTES = 30;

    private final EquipmentDAO equipmentDAO;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ──────────────────────────── Inner Record ────────────────────────────

    /**
     * Immutable report summarising the results of a maintenance scan.
     *
     * @param totalScanned          number of machines inspected
     * @param warningsIssued        machines with usage ≥ 90%
     * @param flaggedForMaintenance machines with usage ≥ 100% (newly flagged)
     * @param alreadyUnderMaintenance machines that were already flagged
     * @param scanTimestamp         when the scan was performed
     */
    public record MaintenanceReport(
            int totalScanned,
            int warningsIssued,
            int flaggedForMaintenance,
            int alreadyUnderMaintenance,
            LocalDateTime scanTimestamp
    ) {
        @Override
        public String toString() {
            return String.format(
                    "MaintenanceReport{scanned=%d, warnings=%d, flagged=%d, alreadyMaintaining=%d, at=%s}",
                    totalScanned, warningsIssued, flaggedForMaintenance,
                    alreadyUnderMaintenance,
                    scanTimestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
    }

    // ──────────────────────────── Constructor ─────────────────────────────

    /**
     * Creates a new maintenance service backed by the given DAO.
     *
     * @param equipmentDAO DAO for equipment persistence
     */
    public EquipmentMaintenanceService(EquipmentDAO equipmentDAO) {
        this.equipmentDAO = Objects.requireNonNull(equipmentDAO, "equipmentDAO must not be null");
    }

    // ──────────────────────── Daemon Lifecycle ────────────────────────────

    /**
     * Starts the background daemon that runs maintenance scans at the
     * specified interval.
     *
     * @param intervalMinutes minutes between scans (must be positive)
     */
    public void startDaemon(long intervalMinutes) {
        if (intervalMinutes <= 0) {
            throw new IllegalArgumentException("Interval must be positive, got: " + intervalMinutes);
        }
        if (running.compareAndSet(false, true)) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "EquipmentMaintenanceDaemon");
                t.setDaemon(true);
                return t;
            });
            scheduledTask = scheduler.scheduleAtFixedRate(
                    this::runMaintenanceScanSafe,
                    0, intervalMinutes, TimeUnit.MINUTES);

            LOGGER.info("Equipment maintenance daemon STARTED — scanning every " +
                        intervalMinutes + " minute(s).");
        } else {
            LOGGER.warning("Daemon is already running. Stop it first before restarting.");
        }
    }

    /**
     * Starts the daemon with the {@link #DEFAULT_INTERVAL_MINUTES default interval}.
     */
    public void startDaemon() {
        startDaemon(DEFAULT_INTERVAL_MINUTES);
    }

    /**
     * Gracefully stops the background daemon.
     * Waits up to 10 seconds for any in-progress scan to finish.
     */
    public void stopDaemon() {
        if (running.compareAndSet(true, false)) {
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
            }
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                        LOGGER.warning("Daemon did not terminate gracefully — forced shutdown.");
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            LOGGER.info("Equipment maintenance daemon STOPPED.");
        } else {
            LOGGER.fine("Daemon was not running.");
        }
    }

    /**
     * Returns whether the daemon is currently active.
     */
    public boolean isDaemonRunning() {
        return running.get();
    }

    // ──────────────────────── Maintenance Scan ────────────────────────────

    /**
     * Exception-safe wrapper for {@link #runMaintenanceScan()}, used by the
     * scheduled executor so that exceptions don't silently kill the daemon.
     */
    private void runMaintenanceScanSafe() {
        try {
            runMaintenanceScan();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Maintenance scan failed unexpectedly", e);
        }
    }

    /**
     * Runs a full maintenance scan across all registered medical machines.
     * <p>
     * For each machine:
     * <ul>
     *   <li>If already under maintenance — counted but not re-flagged.</li>
     *   <li>If usage ≥ 100% — flagged for maintenance in the database.</li>
     *   <li>If usage ≥ 90% — a warning is logged.</li>
     * </ul>
     *
     * @return a {@link MaintenanceReport} summarising the results
     */
    public MaintenanceReport runMaintenanceScan() {
        LOGGER.info("Starting equipment maintenance scan...");

        List<MedicalMachine> machines = equipmentDAO.findAll();
        int totalScanned = machines.size();
        int warningsIssued = 0;
        int flaggedForMaintenance = 0;
        int alreadyUnderMaintenance = 0;

        for (MedicalMachine machine : machines) {
            if (machine.isUnderMaintenance()) {
                alreadyUnderMaintenance++;
                continue;
            }

            double usage = machine.getUsagePercentage();

            if (usage >= CRITICAL_THRESHOLD) {
                // Machine has exceeded safe limit → flag for maintenance
                equipmentDAO.flagForMaintenance(machine.getMachineId());
                flaggedForMaintenance++;
                LOGGER.warning(String.format(
                        "CRITICAL: %s (id=%d, S/N=%s) at %.1f%% usage (%.1f/%.0f hrs) — FLAGGED FOR MAINTENANCE",
                        machine.getEquipmentType().getDisplayName(),
                        machine.getMachineId(), machine.getSerialNumber(),
                        usage, machine.getOperatingHoursUsed(),
                        (double) machine.getSafeOperatingHourLimit()));

            } else if (usage >= WARNING_THRESHOLD) {
                warningsIssued++;
                LOGGER.warning(String.format(
                        "WARNING: %s (id=%d, S/N=%s) at %.1f%% usage — %.1f hrs remaining",
                        machine.getEquipmentType().getDisplayName(),
                        machine.getMachineId(), machine.getSerialNumber(),
                        usage, machine.getRemainingHours()));
            }
        }

        MaintenanceReport report = new MaintenanceReport(
                totalScanned, warningsIssued, flaggedForMaintenance,
                alreadyUnderMaintenance, LocalDateTime.now());

        LOGGER.info("Maintenance scan complete: " + report);
        return report;
    }

    // ──────────────────────── Repair & Hours ─────────────────────────────

    /**
     * Marks a machine as repaired — resets operating hours to zero and
     * clears the maintenance flag.
     *
     * @param machineId the machine to repair
     * @return an {@link AuditLogEntry} recording the repair
     */
    public AuditLogEntry repairMachine(long machineId) {
        equipmentDAO.resetOperatingHours(machineId);
        LOGGER.info("Machine id=" + machineId + " has been repaired — hours reset to 0.");

        AuditLogEntry auditEntry = new AuditLogEntry(
                "MAINTENANCE_SYSTEM",
                "EQUIPMENT_REPAIR",
                String.valueOf(machineId),
                "Machine id=" + machineId + " repaired and operating hours reset to 0."
        );
        return auditEntry;
    }

    /**
     * Adds operating hours to a machine and updates the database.
     * <p>
     * If adding the hours would exceed the safe operating limit,
     * the machine is automatically flagged for maintenance.
     * </p>
     *
     * @param machineId the machine to update
     * @param hours     the hours to add (must be positive)
     * @throws EquipmentLimitExceededException if the machine would exceed its safe limit
     */
    public void addOperatingHours(long machineId, double hours) {
        if (hours <= 0) {
            throw new IllegalArgumentException("Hours must be positive, got: " + hours);
        }

        // Check the current state first
        var optMachine = equipmentDAO.findById(machineId);
        if (optMachine.isEmpty()) {
            LOGGER.warning("Machine id=" + machineId + " not found for hours update.");
            return;
        }

        MedicalMachine machine = optMachine.get();
        if (machine.isUnderMaintenance()) {
            throw new EquipmentLimitExceededException(
                    "Machine id=" + machineId + " is under maintenance and cannot be operated.");
        }

        double projectedTotal = machine.getOperatingHoursUsed() + hours;
        if (projectedTotal > machine.getSafeOperatingHourLimit()) {
            // Flag for maintenance and throw
            equipmentDAO.flagForMaintenance(machineId);
            throw new EquipmentLimitExceededException(String.format(
                    "Machine %s (id=%d) would exceed safe limit: %.1f + %.1f = %.1f hrs (limit: %d hrs). " +
                    "Machine has been flagged for maintenance.",
                    machine.getEquipmentType().getDisplayName(), machineId,
                    machine.getOperatingHoursUsed(), hours, projectedTotal,
                    machine.getSafeOperatingHourLimit()));
        }

        equipmentDAO.addOperatingHours(machineId, hours);
        LOGGER.fine("Added " + hours + " operating hours to machine id=" + machineId);
    }

    // ──────────────────────── Query Operations ───────────────────────────

    /**
     * Returns all medical machines deployed at the specified hospital.
     *
     * @param hospitalName the hospital to query
     * @return list of machines (never {@code null})
     */
    public List<MedicalMachine> getEquipmentStatus(String hospitalName) {
        if (hospitalName == null || hospitalName.trim().isEmpty() || hospitalName.equalsIgnoreCase("All") || hospitalName.equalsIgnoreCase("All Hospitals")) {
            return equipmentDAO.findAll();
        }
        return equipmentDAO.findByHospital(hospitalName);
    }

    /**
     * Returns all machines that currently need maintenance.
     *
     * @return list of machines needing maintenance (never {@code null})
     */
    public List<MedicalMachine> getMachinesNeedingMaintenance() {
        return equipmentDAO.findMachinesNeedingMaintenance();
    }
}
