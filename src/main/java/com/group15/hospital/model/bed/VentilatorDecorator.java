package com.group15.hospital.model.bed;

import com.group15.hospital.model.enums.EquipmentType;

import java.util.List;

/**
 * Concrete decorator that attaches a <strong>Ventilator</strong> to a bed.
 * <p>
 * Adds {@link EquipmentType#VENTILATOR} hourly cost ($150/hr) and appends
 * the ventilator label to the bed's description.
 * </p>
 *
 * @see BedEquipmentDecorator
 * @see EquipmentType#VENTILATOR
 */
public class VentilatorDecorator extends BedEquipmentDecorator {

    private static final EquipmentType TYPE = EquipmentType.VENTILATOR;

    /**
     * Decorates the given bed with a ventilator.
     *
     * @param bed the bed component to decorate
     * @throws com.group15.hospital.exception.EquipmentLimitExceededException
     *         if equipment limits are violated
     */
    public VentilatorDecorator(BedComponent bed) {
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
