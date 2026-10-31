package com.group15.hospital.model.state;

import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.enums.BedStatus;

/**
 * Concrete State representing a bed reserved for an incoming ambulance patient (SRS F8 / U5).
 * <p>
 * A bed in this state is temporarily locked for up to 15 minutes while the ambulance travels.
 * Allowed transitions:
 * <ul>
 *   <li>{@link #assignPatient()} -> {@link OccupiedState} (U5 Step 5: Paramedic/patient arrives and is admitted)</li>
 *   <li>{@link #cancelOrExpireReservation()} -> {@link VacantState} (U5 Exception 2: Expiry, or U5 Alt Flow 1: Rerouted)</li>
 * </ul>
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class ReservedEnRouteState implements BedState {

    @Override
    public BedState assignPatient() {
        // Patient arrives via ambulance and occupies the reserved bed
        return new OccupiedState();
    }

    @Override
    public BedState dischargePatient() {
        throw new IllegalBedStateException("Cannot discharge patient: bed is in RESERVED_EN_ROUTE state with no admitted occupant.");
    }

    @Override
    public BedState reserveEnRoute() {
        throw new IllegalBedStateException("Cannot reserve bed: bed is already RESERVED_EN_ROUTE for another incoming ambulance.");
    }

    @Override
    public BedState cancelOrExpireReservation() {
        // Reservation window expired without arrival or ambulance rerouted to partner hospital
        return new VacantState();
    }

    @Override
    public BedState markSanitized() {
        throw new IllegalBedStateException("Cannot mark sanitized: bed is in RESERVED_EN_ROUTE state.");
    }

    @Override
    public BedState restrictBuffer() {
        throw new IllegalBedStateException("Cannot lock bed as isolation buffer: bed is currently held in RESERVED_EN_ROUTE state.");
    }

    @Override
    public BedState releaseBuffer() {
        throw new IllegalBedStateException("Cannot release buffer: bed is in RESERVED_EN_ROUTE state, not RESTRICTED_BUFFER.");
    }

    @Override
    public BedStatus getStatus() {
        return BedStatus.RESERVED_EN_ROUTE;
    }

    @Override
    public boolean canAcceptPatient() {
        // Accepts the incoming reserved patient upon arrival
        return true;
    }

    @Override
    public boolean canReserveEnRoute() {
        // Cannot double-reserve
        return false;
    }

    @Override
    public String getStateDescription() {
        return "Bed is reserved for an incoming ambulance patient en route.";
    }

    @Override
    public String toString() {
        return "ReservedEnRouteState{status=RESERVED_EN_ROUTE}";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ReservedEnRouteState;
    }

    @Override
    public int hashCode() {
        return ReservedEnRouteState.class.hashCode();
    }
}
