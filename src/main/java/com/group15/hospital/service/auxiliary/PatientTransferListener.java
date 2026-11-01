package com.group15.hospital.service.auxiliary;

import com.group15.hospital.model.bed.BedComponent;
import com.group15.hospital.model.hierarchy.Patient;

/**
 * Observer interface defining patient lifecycle and ward-transfer event hooks (SRS F11, F14 &amp; Use Case U3).
 * <p>
 * Implemented by auxiliary services such as {@link BillingService} and {@link KitchenService}
 * to automatically respond when patients are admitted, moved between hospital wards/ICUs,
 * or discharged.
 * </p>
 *
 * <h3>Design Pattern — Observer (GoF)</h3>
 * <ul>
 *   <li><b>Subject:</b> {@code IsolationContainmentService} (or Transfer Orchestrator)</li>
 *   <li><b>Observers:</b> {@link BillingService}, {@link KitchenService}</li>
 * </ul>
 *
 * @author Jatin (Module P3)
 * @see BillingService
 * @see KitchenService
 */
public interface PatientTransferListener {

    /**
     * Triggered when a patient is admitted into an initial hospital bed.
     *
     * @param patient     the admitted patient
     * @param assignedBed the newly assigned bed component
     */
    void onPatientAdmitted(Patient patient, BedComponent assignedBed);

    /**
     * Triggered when a patient is transferred from one bed/ward to another.
     *
     * @param patient                the patient being transferred
     * @param fromBed                the source bed component released
     * @param toBed                  the destination bed component occupied
     * @param hoursStayedInFromBed   billable duration (in hours) spent in the source bed
     */
    void onPatientTransferred(Patient patient, BedComponent fromBed, BedComponent toBed, long hoursStayedInFromBed);

    /**
     * Triggered when a patient is formally discharged from the hospital.
     *
     * @param patient                the patient being discharged
     * @param releasedBed            the last occupied bed component being vacated
     * @param hoursStayedInLastBed   billable duration (in hours) spent in the final bed
     */
    void onPatientDischarged(Patient patient, BedComponent releasedBed, long hoursStayedInLastBed);
}
