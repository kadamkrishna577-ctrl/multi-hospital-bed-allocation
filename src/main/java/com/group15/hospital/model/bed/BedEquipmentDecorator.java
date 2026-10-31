package com.group15.hospital.model.bed;

import com.group15.hospital.exception.EquipmentLimitExceededException;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Abstract decorator in the Decorator pattern — wraps an existing
 * {@link BedComponent} and adds medical equipment to it.
 * <p>
 * <strong>Constraints enforced at construction time:</strong>
 * <ul>
 *   <li>A bed may have at most {@value #MAX_EQUIPMENT_PER_BED} pieces of equipment.</li>
 *   <li>No two decorators of the same {@link EquipmentType} may be stacked.</li>
 * </ul>
 * Violating either constraint throws {@link EquipmentLimitExceededException}.
 * </p>
 *
 * @see BedComponent
 * @see BaseBed
 */
public abstract class BedEquipmentDecorator implements BedComponent {

    /** Maximum number of equipment items that can be attached to a single bed. */
    public static final int MAX_EQUIPMENT_PER_BED = 4;

    /** The wrapped bed component (may itself be a decorator). */
    protected final BedComponent wrappedBed;

    // ──────────────────────────── Constructor ─────────────────────────────

    /**
     * Wraps the given bed component, validating equipment constraints.
     *
     * @param bed            the bed component to decorate
     * @param equipmentType  the equipment type this decorator adds
     * @throws EquipmentLimitExceededException if max equipment reached or duplicate type
     * @throws NullPointerException            if {@code bed} or {@code equipmentType} is null
     */
    protected BedEquipmentDecorator(BedComponent bed, EquipmentType equipmentType) {
        this.wrappedBed = Objects.requireNonNull(bed, "bed must not be null");
        Objects.requireNonNull(equipmentType, "equipmentType must not be null");

        List<EquipmentType> existing = bed.getAttachedEquipment();

        // Guard: maximum equipment limit
        if (existing.size() >= MAX_EQUIPMENT_PER_BED) {
            throw new EquipmentLimitExceededException(
                    "Bed #" + bed.getBedId() + " already has " + existing.size() +
                    " equipment items attached (max " + MAX_EQUIPMENT_PER_BED + ").");
        }

        // Guard: no duplicate equipment types
        if (existing.contains(equipmentType)) {
            throw new EquipmentLimitExceededException(
                    "Bed #" + bed.getBedId() + " already has a " +
                    equipmentType.getDisplayName() + " attached. Duplicate equipment is not allowed.");
        }
    }

    // ──────────────── Delegated identity methods ─────────────────────

    @Override
    public long getBedId() {
        return wrappedBed.getBedId();
    }

    @Override
    public String getWardName() {
        return wrappedBed.getWardName();
    }

    @Override
    public String getHospitalName() {
        return wrappedBed.getHospitalName();
    }

    // ─────────────── Cost & Equipment (to be overridden) ─────────────

    @Override
    public String getDescription() {
        return wrappedBed.getDescription();
    }

    @Override
    public double getHourlyCost() {
        return wrappedBed.getHourlyCost();
    }

    @Override
    public List<EquipmentType> getAttachedEquipment() {
        return wrappedBed.getAttachedEquipment();
    }

    // ──────────────── Delegated status methods ───────────────────────

    @Override
    public BedStatus getBedStatus() {
        return wrappedBed.getBedStatus();
    }

    @Override
    public void setBedStatus(BedStatus status) {
        wrappedBed.setBedStatus(status);
    }

    // ──────────────── Delegated patient methods ──────────────────────

    @Override
    public Long getCurrentPatientId() {
        return wrappedBed.getCurrentPatientId();
    }

    @Override
    public String getCurrentPatientName() {
        return wrappedBed.getCurrentPatientName();
    }

    @Override
    public TriageLevel getCurrentTriageLevel() {
        return wrappedBed.getCurrentTriageLevel();
    }

    @Override
    public void assignPatient(long patientId, String patientName, TriageLevel triageLevel) {
        wrappedBed.assignPatient(patientId, patientName, triageLevel);
    }

    @Override
    public void dischargePatient() {
        wrappedBed.dischargePatient();
    }

    // ──────────────── Helper for concrete decorators ─────────────────

    /**
     * Builds a new equipment list containing all equipment from the wrapped
     * bed plus the given additional type.
     *
     * @param additionalType the equipment type added by the concrete decorator
     * @return an unmodifiable list with the new type appended
     */
    protected List<EquipmentType> buildEquipmentList(EquipmentType additionalType) {
        List<EquipmentType> combined = new ArrayList<>(wrappedBed.getAttachedEquipment());
        combined.add(additionalType);
        return Collections.unmodifiableList(combined);
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
