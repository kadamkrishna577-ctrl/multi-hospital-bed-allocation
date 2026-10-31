package com.group15.hospital.model.ambulance;

import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Basic Life Support (BLS) Ambulance.
 * <p>
 * Designated for stable, non-life-threatening patient transport (Levels 3, 4, 5).
 * Carries basic equipment such as oxygen tanks. Per SRS F9 and U5 Exception 3,
 * BLS units are prohibited from transporting unassisted Level 1 or Level 2 cardiac/trauma cases.
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class BLSAmbulance extends Ambulance {

    public static final String TYPE = "BLS";

    private static final Set<EquipmentType> SUPPORTED_EQUIPMENT = Collections.unmodifiableSet(
            EnumSet.of(EquipmentType.OXYGEN)
    );

    public BLSAmbulance(String ambulanceId, String licensePlate, String currentHospitalName, boolean available) {
        super(ambulanceId, licensePlate, currentHospitalName, available);
    }

    public BLSAmbulance(String ambulanceId, String licensePlate, String currentHospitalName) {
        this(ambulanceId, licensePlate, currentHospitalName, true);
    }

    @Override
    public String getAmbulanceType() {
        return TYPE;
    }

    /**
     * BLS is compatible with moderate and low acuity cases (Levels 3, 4, 5).
     * Level 1 (Critical) and Level 2 (Emergency) return {@code false} to flag clinical risk.
     *
     * @param triageLevel patient triage
     * @return {@code false} for LEVEL_1 and LEVEL_2; {@code true} for LEVEL_3, LEVEL_4, LEVEL_5
     */
    @Override
    public boolean isSeverityCompatible(TriageLevel triageLevel) {
        if (triageLevel == null) {
            return false;
        }
        return triageLevel != TriageLevel.LEVEL_1 && triageLevel != TriageLevel.LEVEL_2;
    }

    @Override
    public Set<EquipmentType> getSupportedEquipment() {
        return SUPPORTED_EQUIPMENT;
    }
}
