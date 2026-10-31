package com.group15.hospital.model.ambulance;

import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Advanced Life Support (ALS) Ambulance.
 * <p>
 * Specifically designated for Level 1 (Critical) and Level 2 (Emergency) cardiac,
 * trauma, and respiratory failure cases. Equipped with life-support equipment including
 * ventilators, oxygen cylinders, and cardiac monitors (SRS F9, Use Case U5).
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class ALSAmbulance extends Ambulance {

    public static final String TYPE = "ALS";

    private static final Set<EquipmentType> SUPPORTED_EQUIPMENT = Collections.unmodifiableSet(
            EnumSet.of(EquipmentType.VENTILATOR, EquipmentType.OXYGEN, EquipmentType.MONITOR)
    );

    public ALSAmbulance(String ambulanceId, String licensePlate, String currentHospitalName, boolean available) {
        super(ambulanceId, licensePlate, currentHospitalName, available);
    }

    public ALSAmbulance(String ambulanceId, String licensePlate, String currentHospitalName) {
        this(ambulanceId, licensePlate, currentHospitalName, true);
    }

    @Override
    public String getAmbulanceType() {
        return TYPE;
    }

    /**
     * ALS units are clinically required for high and moderate acuity patients (Levels 1, 2, 3).
     * For Level 4 and 5, ALS is clinically capable of transport, but represents over-allocation.
     *
     * @param triageLevel patient triage
     * @return {@code true} for LEVEL_1, LEVEL_2, and LEVEL_3
     */
    @Override
    public boolean isSeverityCompatible(TriageLevel triageLevel) {
        if (triageLevel == null) {
            return false;
        }
        return triageLevel == TriageLevel.LEVEL_1
                || triageLevel == TriageLevel.LEVEL_2
                || triageLevel == TriageLevel.LEVEL_3;
    }

    @Override
    public Set<EquipmentType> getSupportedEquipment() {
        return SUPPORTED_EQUIPMENT;
    }
}
