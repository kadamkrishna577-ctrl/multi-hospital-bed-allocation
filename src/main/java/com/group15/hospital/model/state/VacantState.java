package com.group15.hospital.model.state;

import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.enums.BedStatus;

/**
 * Concrete State representing a bed that is clean, unoccupied, and ready for immediate use.
 * <p>
 * Allowed transitions:
 * <ul>
 *   <li>{@link #assignPatient()} -> {@link OccupiedState} (U1)</li>
 *   <li>{@link #reserveEnRoute()} -> {@link ReservedEnRouteState} (U5)</li>
 *   <li>{@link #restrictBuffer()} -> {@link RestrictedBufferState} (U4)</li>
 * </ul>
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class VacantState implements BedState {

    @Override
    public BedState assignPatient() {
        return new OccupiedState();
    }

    @Override
    public BedState dischargePatient() {
        throw new IllegalBedStateException("Cannot discharge patient: bed is already VACANT.");
    }

    @Override
    public BedState reserveEnRoute() {
        return new ReservedEnRouteState();
    }

    @Override
    public BedState cancelOrExpireReservation() {
        throw new IllegalBedStateException("Cannot cancel reservation: bed is VACANT and not currently reserved.");
    }

    @Override
    public BedState markSanitized() {
        throw new IllegalBedStateException("Cannot mark sanitized: bed is already clean and VACANT.");
    }

    @Override
    public BedState restrictBuffer() {
        return new RestrictedBufferState();
    }

    @Override
    public BedState releaseBuffer() {
        throw new IllegalBedStateException("Cannot release buffer: bed is VACANT, not in RESTRICTED_BUFFER state.");
    }

    @Override
    public BedStatus getStatus() {
        return BedStatus.VACANT;
    }

    @Override
    public boolean canAcceptPatient() {
        return true;
    }

    @Override
    public boolean canReserveEnRoute() {
        return true;
    }

    @Override
    public String getStateDescription() {
        return "Bed is clean, vacant, and available for patient allocation or ambulance reservation.";
    }

    @Override
    public String toString() {
        return "VacantState{status=VACANT}";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof VacantState;
    }

    @Override
    public int hashCode() {
        return VacantState.class.hashCode();
    }
}
