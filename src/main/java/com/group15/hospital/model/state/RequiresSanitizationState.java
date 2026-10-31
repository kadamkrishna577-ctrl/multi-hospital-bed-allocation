package com.group15.hospital.model.state;

import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.enums.BedStatus;

/**
 * Concrete State representing a bed that was recently discharged and requires terminal cleaning
 * and disinfection by sanitation staff (SRS F10, Use Case U6).
 * <p>
 * SRS F10 strictly forbids assigning or reserving beds in this state until sanitization is logged.
 * Allowed transition:
 * <ul>
 *   <li>{@link #markSanitized()} -> {@link VacantState} (U6 Step 4: Cleaning confirmed)</li>
 * </ul>
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class RequiresSanitizationState implements BedState {

    @Override
    public BedState assignPatient() {
        // Enforces SRS F10: Patient cannot be assigned until cleaning is complete
        throw new IllegalBedStateException(
                "Cannot assign patient: bed is in REQUIRES_SANITIZATION state and must be sanitized before reuse.");
    }

    @Override
    public BedState dischargePatient() {
        throw new IllegalBedStateException("Cannot discharge: bed is already vacant and waiting for sanitization.");
    }

    @Override
    public BedState reserveEnRoute() {
        // Enforces SRS F10: Ambulance cannot reserve an unsanitized bed
        throw new IllegalBedStateException(
                "Cannot reserve bed: bed is in REQUIRES_SANITIZATION state and is not clean for patient use.");
    }

    @Override
    public BedState cancelOrExpireReservation() {
        throw new IllegalBedStateException("Cannot cancel reservation: bed is in REQUIRES_SANITIZATION, not RESERVED_EN_ROUTE.");
    }

    @Override
    public BedState markSanitized() {
        // Cleaning staff confirms room is disinfected -> bed becomes available again
        return new VacantState();
    }

    @Override
    public BedState restrictBuffer() {
        throw new IllegalBedStateException("Cannot lock bed as isolation buffer: bed must be sanitized first.");
    }

    @Override
    public BedState releaseBuffer() {
        throw new IllegalBedStateException("Cannot release buffer: bed is in REQUIRES_SANITIZATION, not RESTRICTED_BUFFER.");
    }

    @Override
    public BedStatus getStatus() {
        return BedStatus.REQUIRES_SANITIZATION;
    }

    @Override
    public boolean canAcceptPatient() {
        return false;
    }

    @Override
    public boolean canReserveEnRoute() {
        return false;
    }

    @Override
    public String getStateDescription() {
        return "Bed has been discharged and requires terminal sanitization by cleaning staff before reuse.";
    }

    @Override
    public String toString() {
        return "RequiresSanitizationState{status=REQUIRES_SANITIZATION}";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RequiresSanitizationState;
    }

    @Override
    public int hashCode() {
        return RequiresSanitizationState.class.hashCode();
    }
}
