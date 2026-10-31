package com.group15.hospital.model.ambulance;

import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Abstract domain model representing an emergency medical transit vehicle (Ambulance).
 * <p>
 * Subclassed into {@link ALSAmbulance} (Advanced Life Support) and {@link BLSAmbulance}
 * (Basic Life Support) according to SRS F9 and Use Case U5.
 * </p>
 *
 * @author Siddhant (Module P4)
 * @see ALSAmbulance
 * @see BLSAmbulance
 */
public abstract class Ambulance {

    protected final String ambulanceId;
    protected final String licensePlate;
    protected String currentHospitalName;
    protected boolean available;

    /**
     * Constructs an ambulance instance.
     *
     * @param ambulanceId         unique vehicle identifier (e.g. "AMB-101")
     * @param licensePlate        vehicle registration license plate
     * @param currentHospitalName base or destination hospital name
     * @param available           whether the ambulance is currently free for dispatch
     */
    public Ambulance(String ambulanceId, String licensePlate, String currentHospitalName, boolean available) {
        this.ambulanceId = Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        this.licensePlate = Objects.requireNonNull(licensePlate, "licensePlate must not be null");
        this.currentHospitalName = (currentHospitalName != null) ? currentHospitalName : "Unassigned";
        this.available = available;
    }

    /**
     * Returns the ambulance classification code ("ALS" or "BLS").
     *
     * @return "ALS" or "BLS"
     */
    public abstract String getAmbulanceType();

    /**
     * Evaluates whether this ambulance type is clinically appropriate for the patient's triage severity
     * per SRS F9 (ALS for heart/critical cases, BLS for minor injuries).
     *
     * @param triageLevel patient acuity
     * @return {@code true} if compatible without severe clinical mismatch
     */
    public abstract boolean isSeverityCompatible(TriageLevel triageLevel);

    /**
     * Returns the set of medical equipment supported onboard this vehicle.
     *
     * @return unmodifiable set of equipment types
     */
    public abstract Set<EquipmentType> getSupportedEquipment();

    /**
     * Validates vehicle suitability against the patient's condition and life-support requirements
     * per SRS F9 and Use Case U5 Exception 3.
     *
     * @param patientTriage   the clinical triage level
     * @param neededEquipment the required life-support hardware (ventilator, cardiac monitor, etc.)
     * @return an {@link Optional} containing a warning message if a mismatch exists, or empty if aligned
     */
    public Optional<String> getSeverityWarning(TriageLevel patientTriage, Set<EquipmentType> neededEquipment) {
        Set<EquipmentType> required = (neededEquipment != null) ? neededEquipment : Collections.emptySet();

        // Check if patient needs critical equipment that vehicle lacks
        for (EquipmentType eq : required) {
            if (!getSupportedEquipment().contains(eq)) {
                return Optional.of(String.format(
                        "CRITICAL MISMATCH: Patient requires %s, but %s vehicle %s does not carry this equipment! " +
                        "An Advanced Life Support (ALS) unit is required.",
                        eq.getDisplayName(), getAmbulanceType(), ambulanceId
                ));
            }
        }

        // Check BLS carrying high-acuity critical patient
        if ("BLS".equalsIgnoreCase(getAmbulanceType())) {
            if (patientTriage == TriageLevel.LEVEL_1 || patientTriage == TriageLevel.LEVEL_2) {
                return Optional.of(String.format(
                        "HIGH-RISK WARNING: Patient is %s (%s), but vehicle %s is Basic Life Support (BLS). " +
                        "Cardiac/ventilator complications require an Advanced Life Support (ALS) unit.",
                        patientTriage.name(), patientTriage.getDescription(), ambulanceId
                ));
            }
        }

        // Check ALS carrying non-urgent patient (resource over-allocation warning)
        if ("ALS".equalsIgnoreCase(getAmbulanceType())) {
            if (patientTriage == TriageLevel.LEVEL_4 || patientTriage == TriageLevel.LEVEL_5) {
                if (required.isEmpty()) {
                    return Optional.of(String.format(
                            "ADVISORY: Low-acuity patient (%s - %s) assigned to Advanced Life Support (ALS) %s. " +
                            "Consider utilizing a BLS unit to preserve critical care transit capacity.",
                            patientTriage.name(), patientTriage.getDescription(), ambulanceId
                    ));
                }
            }
        }

        return Optional.empty();
    }

    public String getAmbulanceId() {
        return ambulanceId;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getCurrentHospitalName() {
        return currentHospitalName;
    }

    public void setCurrentHospitalName(String currentHospitalName) {
        this.currentHospitalName = currentHospitalName;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ambulance other)) return false;
        return Objects.equals(ambulanceId, other.ambulanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(ambulanceId);
    }

    @Override
    public String toString() {
        return String.format("%s[ID=%s, Plate=%s, Hospital=%s, Available=%s]",
                getClass().getSimpleName(), ambulanceId, licensePlate, currentHospitalName, available);
    }
}
