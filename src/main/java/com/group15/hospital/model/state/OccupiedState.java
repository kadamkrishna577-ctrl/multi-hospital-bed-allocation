package com.group15.hospital.model.state;

import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.enums.BedStatus;

/**
 * Concrete State representing a bed currently occupied by an admitted patient.
 * <p>
 * Allowed transition:
 * <ul>
 *   <li>{@link #dischargePatient()} -> {@link RequiresSanitizationState} (SRS F10: mandatory cleaning after discharge)</li>
 * </ul>
 * All other transitions throw {@link IllegalBedStateException}.
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class OccupiedState implements BedState {

    @Override
    public BedState assignPatient() {
        throw new IllegalBedStateException("Cannot assign patient: bed is already OCCUPIED by another patient.");
    }

    @Override
    public BedState dischargePatient() {
        // Enforces SRS F10: Must undergo terminal sanitization after discharge
        return new RequiresSanitizationState();
    }

    @Override
    public BedState reserveEnRoute() {
        throw new IllegalBedStateException("Cannot reserve bed for ambulance: bed is currently OCCUPIED.");
    }

    @Override
    public BedState cancelOrExpireReservation() {
        throw new IllegalBedStateException("Cannot cancel reservation: bed is OCCUPIED, not in RESERVED_EN_ROUTE state.");
    }

    @Override
    public BedState markSanitized() {
        throw new IllegalBedStateException("Cannot sanitize bed: bed is currently OCCUPIED by a patient.");
    }

    @Override
    public BedState restrictBuffer() {
        throw new IllegalBedStateException("Cannot lock bed as isolation buffer: bed is currently OCCUPIED.");
    }

    @Override
    public BedState releaseBuffer() {
        throw new IllegalBedStateException("Cannot release buffer: bed is OCCUPIED, not in RESTRICTED_BUFFER state.");
    }

    @Override
    public BedStatus getStatus() {
        return BedStatus.OCCUPIED;
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
        return "Bed is currently occupied by an admitted patient.";
    }

    @Override
    public String toString() {
        return "OccupiedState{status=OCCUPIED}";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof OccupiedState;
    }

    @Override
    public int hashCode() {
        return OccupiedState.class.hashCode();
    }
}
