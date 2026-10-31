package com.group15.hospital.model.bed;

import com.group15.hospital.model.enums.EquipmentType;

import java.util.List;

/**
 * Concrete decorator that attaches a <strong>Cardiac Monitor</strong> to a bed.
 * <p>
 * Adds {@link EquipmentType#MONITOR} hourly cost ($60/hr) and appends
 * the monitor label to the bed's description.
 * </p>
 *
 * @see BedEquipmentDecorator
 * @see EquipmentType#MONITOR
 */
public class CardiacMonitorDecorator extends BedEquipmentDecorator {

    private static final EquipmentType TYPE = EquipmentType.MONITOR;

    /**
     * Decorates the given bed with a cardiac monitor.
     *
     * @param bed the bed component to decorate
     * @throws com.group15.hospital.exception.EquipmentLimitExceededException
     *         if equipment limits are violated
     */
    public CardiacMonitorDecorator(BedComponent bed) {
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
