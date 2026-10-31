package com.group15.hospital.model.state;

import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.enums.BedStatus;

/**
 * Concrete State representing a bed locked as an infection control safety buffer
 * adjacent to or sharing air circulation with a contagious patient (SRS F12, Use Case U4).
 * <p>
 * A bed in this state cannot be allocated or reserved until the contagious source patient
 * is discharged, their room is cleaned, and the buffer is released.
 * Allowed transition:
 * <ul>
 *   <li>{@link #releaseBuffer()} -> {@link VacantState} (U4 Note: buffer lock lifted)</li>
 * </ul>
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class RestrictedBufferState implements BedState {

    @Override
    public BedState assignPatient() {
        // Enforces SRS F12: Cannot place patients in restricted infection buffers
        throw new IllegalBedStateException(
                "Cannot assign patient: bed is locked in RESTRICTED_BUFFER state for contagious isolation.");
    }

    @Override
    public BedState dischargePatient() {
        throw new IllegalBedStateException("Cannot discharge: bed is an empty isolation buffer with no patient.");
    }

    @Override
    public BedState reserveEnRoute() {
        // Enforces SRS F12: Cannot reserve buffer beds for ambulances
        throw new IllegalBedStateException(
                "Cannot reserve bed: bed is locked in RESTRICTED_BUFFER state for contagious isolation.");
    }

    @Override
    public BedState cancelOrExpireReservation() {
        throw new IllegalBedStateException("Cannot cancel reservation: bed is in RESTRICTED_BUFFER, not RESERVED_EN_ROUTE.");
    }

    @Override
    public BedState markSanitized() {
        throw new IllegalBedStateException("Cannot mark sanitized: bed is locked as an active isolation buffer.");
    }

    @Override
    public BedState restrictBuffer() {
        throw new IllegalBedStateException("Cannot restrict buffer: bed is already locked in RESTRICTED_BUFFER state.");
    }

    @Override
    public BedState releaseBuffer() {
        // Isolation buffer restriction lifted after contagious case resolves
        return new VacantState();
    }

    @Override
    public BedStatus getStatus() {
        return BedStatus.RESTRICTED_BUFFER;
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
        return "Bed is locked as an infection control buffer for an adjacent contagious patient.";
    }

    @Override
    public String toString() {
        return "RestrictedBufferState{status=RESTRICTED_BUFFER}";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RestrictedBufferState;
    }

    @Override
    public int hashCode() {
        return RestrictedBufferState.class.hashCode();
    }
}
