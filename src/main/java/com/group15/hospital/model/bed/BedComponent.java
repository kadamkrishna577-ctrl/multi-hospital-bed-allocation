package com.group15.hospital.model.bed;

import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;

import java.util.List;

/**
 * Component interface for the Decorator pattern in the bed-equipment system.
 * <p>
 * Represents any bed configuration — a plain bed or one decorated with
 * medical equipment. Each decorator layer adds cost, equipment, and
 * description details while delegating core identity and patient state
 * to the underlying {@link BaseBed}.
 * </p>
 *
 * <h3>Design Pattern — Decorator (GoF)</h3>
 * <pre>
 *   BedComponent  (this interface)
 *       ├── BaseBed                   (concrete component)
 *       └── BedEquipmentDecorator     (abstract decorator)
 *               ├── VentilatorDecorator
 *               ├── OxygenCylinderDecorator
 *               ├── DialysisDecorator
 *               └── CardiacMonitorDecorator
 * </pre>
 *
 * @see BaseBed
 * @see BedEquipmentDecorator
 */
public interface BedComponent {

    // ──────────────────────────── Identity ────────────────────────────

    /** Returns the unique bed identifier. */
    long getBedId();

    /** Returns the ward this bed belongs to. */
    String getWardName();

    /** Returns the hospital this bed belongs to. */
    String getHospitalName();

    // ──────────────────────── Cost & Equipment ────────────────────────

    /**
     * Returns a human-readable description of the bed and all attached equipment.
     * Decorators append their equipment names to the base description.
     */
    String getDescription();

    /**
     * Returns the total hourly cost of this bed, including all attached equipment.
     * Each equipment decorator adds its {@link EquipmentType#getHourlyCost()} to the total.
     */
    double getHourlyCost();

    /**
     * Returns an unmodifiable list of all equipment types currently attached to this bed.
     * The base bed returns an empty list; each decorator prepends its type.
     */
    List<EquipmentType> getAttachedEquipment();

    // ────────────────────────── Bed Status ────────────────────────────

    /** Returns the current status of this bed. */
    BedStatus getBedStatus();

    /**
     * Updates the status of this bed.
     *
     * @param status the new bed status
     */
    void setBedStatus(BedStatus status);

    // ──────────────────────── Patient State ───────────────────────────

    /** Returns the ID of the patient currently assigned to this bed, or {@code null} if vacant. */
    Long getCurrentPatientId();

    /** Returns the name of the patient currently assigned to this bed, or {@code null} if vacant. */
    String getCurrentPatientName();

    /** Returns the triage level of the currently assigned patient, or {@code null} if vacant. */
    TriageLevel getCurrentTriageLevel();

    /**
     * Assigns a patient to this bed.
     *
     * @param patientId   the patient's unique identifier
     * @param patientName the patient's display name
     * @param triageLevel the patient's triage acuity level
     * @throws com.group15.hospital.exception.IllegalBedStateException
     *         if the bed is not in a state that allows patient assignment
     */
    void assignPatient(long patientId, String patientName, TriageLevel triageLevel);

    /**
     * Discharges the current patient from this bed, transitioning the bed
     * to {@link BedStatus#REQUIRES_SANITIZATION}.
     */
    void dischargePatient();
}
