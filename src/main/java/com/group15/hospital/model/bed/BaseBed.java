package com.group15.hospital.model.bed;

import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Concrete component in the Decorator pattern — a plain ICU bed without
 * any attached medical equipment.
 * <p>
 * A {@code BaseBed} carries a fixed base hourly rate of <strong>$50.00</strong>
 * and starts in the {@link BedStatus#VACANT} state.  Patient assignment is
 * guarded by state validation: only beds in {@code VACANT} or
 * {@code RESERVED_EN_ROUTE} states accept new patients.
 * </p>
 *
 * @see BedComponent
 * @see BedEquipmentDecorator
 */
public class BaseBed implements BedComponent {

    /** Base hourly cost for an unequipped ICU bed. */
    public static final double BASE_HOURLY_COST = 50.0;

    // ── Identity ──
    private final long bedId;
    private final String wardName;
    private final String hospitalName;

    // ── State ──
    private BedStatus bedStatus;

    // ── Patient (nullable when vacant) ──
    private Long currentPatientId;
    private String currentPatientName;
    private TriageLevel currentTriageLevel;

    // ──────────────────────────── Constructors ────────────────────────────

    /**
     * Creates a new vacant bed with default status.
     *
     * @param bedId        unique bed identifier
     * @param wardName     the ward this bed belongs to
     * @param hospitalName the hospital this bed belongs to
     */
    public BaseBed(long bedId, String wardName, String hospitalName) {
        this(bedId, wardName, hospitalName, BedStatus.VACANT);
    }

    /**
     * Creates a new bed with the specified initial status.
     *
     * @param bedId        unique bed identifier
     * @param wardName     the ward this bed belongs to
     * @param hospitalName the hospital this bed belongs to
     * @param bedStatus    initial bed status
     */
    public BaseBed(long bedId, String wardName, String hospitalName, BedStatus bedStatus) {
        this.bedId = bedId;
        this.wardName = Objects.requireNonNull(wardName, "wardName must not be null");
        this.hospitalName = Objects.requireNonNull(hospitalName, "hospitalName must not be null");
        this.bedStatus = Objects.requireNonNull(bedStatus, "bedStatus must not be null");
    }

    // ──────────────────────────── Identity ────────────────────────────

    @Override
    public long getBedId() {
        return bedId;
    }

    @Override
    public String getWardName() {
        return wardName;
    }

    @Override
    public String getHospitalName() {
        return hospitalName;
    }

    // ──────────────────────── Cost & Equipment ────────────────────────

    @Override
    public String getDescription() {
        return String.format("Base ICU Bed #%d [%s — %s]", bedId, wardName, hospitalName);
    }

    @Override
    public double getHourlyCost() {
        return BASE_HOURLY_COST;
    }

    /**
     * A base bed has no attached equipment.
     *
     * @return an empty, unmodifiable list
     */
    @Override
    public List<EquipmentType> getAttachedEquipment() {
        return Collections.emptyList();
    }

    // ────────────────────────── Bed Status ────────────────────────────

    @Override
    public synchronized BedStatus getBedStatus() {
        return bedStatus;
    }

    @Override
    public synchronized void setBedStatus(BedStatus status) {
        this.bedStatus = Objects.requireNonNull(status, "status must not be null");
    }

    // ──────────────────────── Patient State ───────────────────────────

    @Override
    public synchronized Long getCurrentPatientId() {
        return currentPatientId;
    }

    @Override
    public synchronized String getCurrentPatientName() {
        return currentPatientName;
    }

    @Override
    public synchronized TriageLevel getCurrentTriageLevel() {
        return currentTriageLevel;
    }

    /**
     * Assigns a patient to this bed.
     * <p>
     * Only beds in {@link BedStatus#VACANT} or {@link BedStatus#RESERVED_EN_ROUTE}
     * accept new patients.  On successful assignment the status transitions to
     * {@link BedStatus#OCCUPIED}.
     * </p>
     *
     * @throws IllegalBedStateException if the bed is in REQUIRES_SANITIZATION,
     *                                  RESTRICTED_BUFFER, or already OCCUPIED
     */
    @Override
    public synchronized void assignPatient(long patientId, String patientName, TriageLevel triageLevel) {
        if (bedStatus == BedStatus.REQUIRES_SANITIZATION) {
            throw new IllegalBedStateException(
                    "Cannot assign patient to bed #" + bedId +
                    ": bed is in RequiresSanitizationState and must be cleaned first.");
        }
        if (bedStatus == BedStatus.RESTRICTED_BUFFER) {
            throw new IllegalBedStateException(
                    "Cannot assign patient to bed #" + bedId +
                    ": bed is in RestrictedBufferState and is temporarily unavailable.");
        }
        if (bedStatus == BedStatus.OCCUPIED) {
            throw new IllegalBedStateException(
                    "Cannot assign patient to bed #" + bedId +
                    ": bed is already occupied by patient " + currentPatientName + ".");
        }

        this.currentPatientId = patientId;
        this.currentPatientName = Objects.requireNonNull(patientName, "patientName must not be null");
        this.currentTriageLevel = Objects.requireNonNull(triageLevel, "triageLevel must not be null");
        this.bedStatus = BedStatus.OCCUPIED;
    }

    /**
     * Forcibly records patient occupancy, used during admissions and synchronization.
     */
    public synchronized void forceAssignPatient(long patientId, String patientName, TriageLevel triageLevel) {
        this.currentPatientId = patientId;
        this.currentPatientName = Objects.requireNonNull(patientName, "patientName must not be null");
        this.currentTriageLevel = Objects.requireNonNull(triageLevel, "triageLevel must not be null");
        this.bedStatus = BedStatus.OCCUPIED;
    }

    /**
     * Discharges the current patient and transitions the bed to
     * {@link BedStatus#REQUIRES_SANITIZATION}.
     */
    @Override
    public synchronized void dischargePatient() {
        this.currentPatientId = null;
        this.currentPatientName = null;
        this.currentTriageLevel = null;
        this.bedStatus = BedStatus.REQUIRES_SANITIZATION;
    }

    // ──────────────────────── Object overrides ────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseBed other)) return false;
        return bedId == other.bedId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(bedId);
    }

    @Override
    public String toString() {
        return String.format("BaseBed{id=%d, ward='%s', hospital='%s', status=%s, patient=%s}",
                bedId, wardName, hospitalName, bedStatus,
                currentPatientName != null ? currentPatientName : "NONE");
    }
}
