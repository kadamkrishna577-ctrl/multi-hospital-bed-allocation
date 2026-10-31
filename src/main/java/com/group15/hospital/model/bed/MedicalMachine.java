package com.group15.hospital.model.bed;

import com.group15.hospital.exception.EquipmentLimitExceededException;
import com.group15.hospital.model.enums.EquipmentType;

import java.util.Objects;

/**
 * Represents a physical medical machine deployed in a hospital.
 * <p>
 * Each machine tracks its cumulative {@link #operatingHoursUsed operating hours}
 * against the {@link EquipmentType#getSafeOperatingHours() safe operating limit}
 * defined by its equipment type.  When the limit is exceeded an
 * {@link EquipmentLimitExceededException} is thrown, and the machine should be
 * flagged for maintenance.
 * </p>
 *
 * <h3>Safe Operating Hour Limits (from {@link EquipmentType})</h3>
 * <ul>
 *   <li>Ventilator — 500 hrs</li>
 *   <li>Oxygen Cylinder — 1 000 hrs</li>
 *   <li>Dialysis Machine — 300 hrs</li>
 *   <li>Cardiac Monitor — 1 500 hrs</li>
 * </ul>
 */
public class MedicalMachine {

    private final long machineId;
    private final EquipmentType equipmentType;
    private final String serialNumber;
    private final String hospitalName;
    private double operatingHoursUsed;
    private boolean underMaintenance;

    // ──────────────────────────── Constructors ────────────────────────────

    /**
     * Creates a new machine with zero operating hours.
     *
     * @param machineId     unique machine identifier
     * @param equipmentType the type of equipment
     * @param serialNumber  manufacturer serial number
     * @param hospitalName  the hospital this machine is deployed at
     */
    public MedicalMachine(long machineId, EquipmentType equipmentType,
                          String serialNumber, String hospitalName) {
        this(machineId, equipmentType, serialNumber, hospitalName, 0.0, false);
    }

    /**
     * Creates a machine with pre-existing operating hours (e.g., loaded from the database).
     *
     * @param machineId          unique machine identifier
     * @param equipmentType      the type of equipment
     * @param serialNumber       manufacturer serial number
     * @param hospitalName       the hospital this machine is deployed at
     * @param operatingHoursUsed cumulative hours the machine has been in operation
     * @param underMaintenance   whether the machine is currently under maintenance
     */
    public MedicalMachine(long machineId, EquipmentType equipmentType,
                          String serialNumber, String hospitalName,
                          double operatingHoursUsed, boolean underMaintenance) {
        this.machineId = machineId;
        this.equipmentType = Objects.requireNonNull(equipmentType, "equipmentType must not be null");
        this.serialNumber = Objects.requireNonNull(serialNumber, "serialNumber must not be null");
        this.hospitalName = Objects.requireNonNull(hospitalName, "hospitalName must not be null");
        this.operatingHoursUsed = Math.max(0, operatingHoursUsed);
        this.underMaintenance = underMaintenance;
    }

    // ──────────────────────────── Getters ─────────────────────────────

    public long getMachineId() {
        return machineId;
    }

    public EquipmentType getEquipmentType() {
        return equipmentType;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public double getOperatingHoursUsed() {
        return operatingHoursUsed;
    }

    public boolean isUnderMaintenance() {
        return underMaintenance;
    }

    // ──────────────────────── Operating Hours ─────────────────────────

    /**
     * Returns the safe operating hour limit for this machine's equipment type.
     */
    public int getSafeOperatingHourLimit() {
        return equipmentType.getSafeOperatingHours();
    }

    /**
     * Returns the number of operating hours remaining before the safe limit is reached.
     * May return a negative value if the limit has already been exceeded.
     */
    public double getRemainingHours() {
        return equipmentType.getSafeOperatingHours() - operatingHoursUsed;
    }

    /**
     * Returns {@code true} if the machine has not yet exceeded its safe operating hours.
     */
    public boolean isWithinSafeOperatingHours() {
        return operatingHoursUsed < equipmentType.getSafeOperatingHours();
    }

    /**
     * Returns the usage percentage (0.0 – 100.0+) relative to the safe operating limit.
     */
    public double getUsagePercentage() {
        return (operatingHoursUsed / equipmentType.getSafeOperatingHours()) * 100.0;
    }

    /**
     * Adds operating hours to the machine's cumulative total.
     *
     * @param hours the number of hours to add (must be positive)
     * @throws EquipmentLimitExceededException if adding these hours would exceed
     *         the safe operating limit
     * @throws IllegalArgumentException        if {@code hours} is not positive
     */
    public void addOperatingHours(double hours) {
        if (hours <= 0) {
            throw new IllegalArgumentException("Hours to add must be positive, got: " + hours);
        }
        double newTotal = operatingHoursUsed + hours;
        if (newTotal > equipmentType.getSafeOperatingHours()) {
            throw new EquipmentLimitExceededException(
                    String.format("Machine %s (S/N: %s) would exceed safe operating limit: " +
                                  "%.1f + %.1f = %.1f hrs (limit: %d hrs). Maintenance required.",
                                  equipmentType.getDisplayName(), serialNumber,
                                  operatingHoursUsed, hours, newTotal,
                                  equipmentType.getSafeOperatingHours()));
        }
        this.operatingHoursUsed = newTotal;
    }

    // ──────────────────────── Maintenance ─────────────────────────────

    /**
     * Marks the machine as under maintenance.
     */
    public void setUnderMaintenance(boolean underMaintenance) {
        this.underMaintenance = underMaintenance;
    }

    /**
     * Resets operating hours to zero after maintenance is complete and
     * clears the maintenance flag.
     */
    public void completeMaintenance() {
        this.operatingHoursUsed = 0.0;
        this.underMaintenance = false;
    }

    // ──────────────────────── Object overrides ────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MedicalMachine other)) return false;
        return machineId == other.machineId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(machineId);
    }

    @Override
    public String toString() {
        return String.format("MedicalMachine{id=%d, type=%s, serial='%s', hospital='%s', " +
                             "hours=%.1f/%d, maintenance=%s}",
                machineId, equipmentType.getDisplayName(), serialNumber, hospitalName,
                operatingHoursUsed, equipmentType.getSafeOperatingHours(), underMaintenance);
    }
}
