package com.group15.hospital.model.bed;

import com.group15.hospital.model.enums.EquipmentType;

import java.util.List;

/**
 * Concrete decorator that attaches a <strong>Dialysis Machine</strong> to a bed.
 * <p>
 * Adds {@link EquipmentType#DIALYSIS} hourly cost ($200/hr) and appends
 * the dialysis label to the bed's description.
 * </p>
 *
 * @see BedEquipmentDecorator
 * @see EquipmentType#DIALYSIS
 */
public class DialysisDecorator extends BedEquipmentDecorator {

    private static final EquipmentType TYPE = EquipmentType.DIALYSIS;

    /**
     * Decorates the given bed with a dialysis machine.
     *
     * @param bed the bed component to decorate
     * @throws com.group15.hospital.exception.EquipmentLimitExceededException
     *         if equipment limits are violated
     */
    public DialysisDecorator(BedComponent bed) {
        super(bed, TYPE);
    }

    @Override
    public String getDescription() {
        return wrappedBed.getDescription() + " + " + TYPE.getDisplayName();
    }

    @Override
    public double getHourlyCost() {
        return wrappedBed.getHourlyCost() + TYPE.getHourlyCost();
    }

    @Override
    public List<EquipmentType> getAttachedEquipment() {
        return buildEquipmentList(TYPE);
    }
}
