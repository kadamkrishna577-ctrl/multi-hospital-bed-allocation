package com.group15.hospital.model.state;

import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.enums.BedStatus;

/**
 * State Pattern interface representing the operational lifecycle of a hospital bed.
 * <p>
 * Controls allowed transitions across the five fundamental bed states:
 * <ul>
 *   <li>{@link VacantState} (VACANT)</li>
 *   <li>{@link OccupiedState} (OCCUPIED)</li>
 *   <li>{@link ReservedEnRouteState} (RESERVED_EN_ROUTE)</li>
 *   <li>{@link RequiresSanitizationState} (REQUIRES_SANITIZATION)</li>
 *   <li>{@link RestrictedBufferState} (RESTRICTED_BUFFER)</li>
 * </ul>
 * Any invalid state transition triggers an {@link IllegalBedStateException}.
 * </p>
 *
 * @author Siddhant (Module P4)
 * @see BedStatus
 * @see IllegalBedStateException
 */
public interface BedState {

    /**
     * Attempts to assign an admitted patient to this bed.
     *
     * @return the resulting {@link BedState} (typically {@link OccupiedState})
     * @throws IllegalBedStateException if the bed cannot accept a patient in its current state
     */
    BedState assignPatient() throws IllegalBedStateException;

    /**
     * Attempts to discharge the current patient from this bed.
     * Transitions the bed to {@link RequiresSanitizationState} per SRS F10.
     *
     * @return the resulting {@link BedState}
     * @throws IllegalBedStateException if the bed is not currently occupied
     */
    BedState dischargePatient() throws IllegalBedStateException;

    /**
     * Reserves this bed for an incoming ambulance patient en route (SRS F8 / U5).
     *
     * @return the resulting {@link BedState} ({@link ReservedEnRouteState})
     * @throws IllegalBedStateException if the bed is not vacant
     */
    BedState reserveEnRoute() throws IllegalBedStateException;

    /**
     * Cancels an en-route reservation or releases it when the 15-minute window expires
     * (SRS F8 Exception 2 / U5 Alt Flow 1).
     *
     * @return the resulting {@link BedState} ({@link VacantState})
     * @throws IllegalBedStateException if the bed is not currently in RESERVED_EN_ROUTE state
     */
    BedState cancelOrExpireReservation() throws IllegalBedStateException;

    /**
     * Marks terminal room sanitization complete by cleaning staff (SRS F10 / U6).
     *
     * @return the resulting {@link BedState} ({@link VacantState})
     * @throws IllegalBedStateException if the bed is not currently in REQUIRES_SANITIZATION state
     */
    BedState markSanitized() throws IllegalBedStateException;

    /**
     * Locks the bed as an infection control safety buffer adjacent to a contagious patient (SRS F12 / U4).
     *
     * @return the resulting {@link BedState} ({@link RestrictedBufferState})
     * @throws IllegalBedStateException if the bed is not currently vacant
     */
    BedState restrictBuffer() throws IllegalBedStateException;

    /**
     * Releases the infection control isolation buffer after the contagious patient is discharged (SRS F12 / U4).
     *
     * @return the resulting {@link BedState} ({@link VacantState})
     * @throws IllegalBedStateException if the bed is not currently locked as a restricted buffer
     */
    BedState releaseBuffer() throws IllegalBedStateException;

    /**
     * Returns the corresponding {@link BedStatus} enum constant for persistence mapping.
     *
     * @return the enum status
     */
    BedStatus getStatus();

    /**
     * Checks if this bed can be directly assigned to a patient.
     *
     * @return {@code true} if an admitted patient can be placed in this bed
     */
    boolean canAcceptPatient();

    /**
     * Checks if this bed can be reserved by an incoming ambulance.
     *
     * @return {@code true} if eligible for ambulance reservation
     */
    boolean canReserveEnRoute();

    /**
     * Returns a human-readable description of this state for UI displays and audit logging.
     *
     * @return state description string
     */
    String getStateDescription();

    /**
     * Static factory method to instantiate the concrete {@link BedState} corresponding
     * to a database-loaded {@link BedStatus} enum constant.
     *
     * @param status the persisted status
     * @return the concrete state object
     * @throws IllegalArgumentException if status is null or unrecognised
     */
    static BedState fromStatus(BedStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("BedStatus cannot be null when creating BedState");
        }
        return switch (status) {
            case VACANT -> new VacantState();
            case OCCUPIED -> new OccupiedState();
            case RESERVED_EN_ROUTE -> new ReservedEnRouteState();
            case REQUIRES_SANITIZATION -> new RequiresSanitizationState();
            case RESTRICTED_BUFFER -> new RestrictedBufferState();
        };
    }
}
