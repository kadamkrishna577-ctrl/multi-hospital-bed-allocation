package com.group15.hospital.model.ambulance;

import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Domain model representing a pre-arrival bed reservation made by an en-route ambulance (SRS F8 / U5).
 * <p>
 * Tracks the bed allocation, the estimated arrival window (default 15 minutes),
 * possible partner hospital rerouting (SRS F9), and status transitions:
 * <ul>
 *   <li>{@code RESERVED_EN_ROUTE} - Active reservation in-flight</li>
 *   <li>{@code ARRIVED_OCCUPIED} - Patient confirmed and admitted into bed</li>
 *   <li>{@code REROUTED} - Original hospital saturated; shifted to partner hospital</li>
 *   <li>{@code EXPIRED} - 15-minute window passed without patient arrival</li>
 *   <li>{@code CANCELLED} - Paramedic or dispatcher aborted transit</li>
 * </ul>
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class AmbulanceReservation {

    public static final String STATUS_RESERVED = "RESERVED_EN_ROUTE";
    public static final String STATUS_ARRIVED = "ARRIVED_OCCUPIED";
    public static final String STATUS_REROUTED = "REROUTED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final int DEFAULT_WINDOW_MINUTES = 15;

    private final long reservationId;
    private final String ambulanceId;
    private final String ambulanceType;
    private final String patientName;
    private final TriageLevel patientTriage;
    private final Set<EquipmentType> requiredEquipment;

    private String hospitalName;
    private String wardName;
    private long assignedBedId;
    private final LocalDateTime reservedAt;
    private LocalDateTime expiresAt;
    private String status;
    private String reroutedFromHospital;

    /**
     * Full constructor including persistent reservationId (e.g. from Database/DAO).
     */
    public AmbulanceReservation(
            long reservationId,
            String ambulanceId,
            String ambulanceType,
            String patientName,
            TriageLevel patientTriage,
            Set<EquipmentType> requiredEquipment,
            String hospitalName,
            String wardName,
            long assignedBedId,
            LocalDateTime reservedAt,
            LocalDateTime expiresAt,
            String status,
            String reroutedFromHospital
    ) {
        this.reservationId = reservationId;
        this.ambulanceId = Objects.requireNonNull(ambulanceId, "ambulanceId must not be null");
        this.ambulanceType = Objects.requireNonNull(ambulanceType, "ambulanceType must not be null");
        this.patientName = (patientName != null && !patientName.isBlank()) ? patientName : "Anonymous Emergency Patient";
        this.patientTriage = (patientTriage != null) ? patientTriage : TriageLevel.LEVEL_1;
        this.requiredEquipment = (requiredEquipment != null)
                ? Collections.unmodifiableSet(new HashSet<>(requiredEquipment))
                : Collections.emptySet();
        this.hospitalName = Objects.requireNonNull(hospitalName, "hospitalName must not be null");
        this.wardName = (wardName != null) ? wardName : "ICU";
        this.assignedBedId = assignedBedId;
        this.reservedAt = (reservedAt != null) ? reservedAt : LocalDateTime.now();
        this.expiresAt = (expiresAt != null) ? expiresAt : this.reservedAt.plusMinutes(DEFAULT_WINDOW_MINUTES);
        this.status = (status != null) ? status : STATUS_RESERVED;
        this.reroutedFromHospital = reroutedFromHospital;
    }

    /**
     * Convenience constructor for creating a brand-new in-flight reservation with default 15-minute window.
     */
    public AmbulanceReservation(
            String ambulanceId,
            String ambulanceType,
            String patientName,
            TriageLevel patientTriage,
            Set<EquipmentType> requiredEquipment,
            String hospitalName,
            String wardName,
            long assignedBedId
    ) {
        this(0L, ambulanceId, ambulanceType, patientName, patientTriage, requiredEquipment,
                hospitalName, wardName, assignedBedId, LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(DEFAULT_WINDOW_MINUTES), STATUS_RESERVED, null);
    }

    /**
     * Evaluates if this reservation has passed its expiry threshold against the provided timestamp.
     *
     * @param now current time reference
     * @return {@code true} if current time is strictly after the expiration timestamp
     */
    public boolean isExpired(LocalDateTime now) {
        if (now == null) {
            now = LocalDateTime.now();
        }
        return now.isAfter(expiresAt);
    }

    /**
     * Extends the reservation window by additional minutes per Use Case U5 Alternative Flow 2
     * (e.g. traffic delay or extended transit distance).
     *
     * @param extraMinutes additional minutes to extend
     * @throws IllegalArgumentException if extraMinutes is negative or zero
     */
    public void extendWindow(int extraMinutes) {
        if (extraMinutes <= 0) {
            throw new IllegalArgumentException("extraMinutes must be strictly positive: " + extraMinutes);
        }
        this.expiresAt = this.expiresAt.plusMinutes(extraMinutes);
    }

    /**
     * Calculates remaining seconds until this reservation expires.
     *
     * @param now current time reference
     * @return remaining seconds (positive), or 0 if already expired
     */
    public long getRemainingSeconds(LocalDateTime now) {
        if (now == null) {
            now = LocalDateTime.now();
        }
        if (now.isAfter(expiresAt)) {
            return 0;
        }
        return Duration.between(now, expiresAt).getSeconds();
    }

    // ── Getters and Setters ──

    public long getReservationId() {
        return reservationId;
    }

    public String getAmbulanceId() {
        return ambulanceId;
    }

    public String getAmbulanceType() {
        return ambulanceType;
    }

    public String getPatientName() {
        return patientName;
    }

    public TriageLevel getPatientTriage() {
        return patientTriage;
    }

    public Set<EquipmentType> getRequiredEquipment() {
        return requiredEquipment;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = Objects.requireNonNull(hospitalName);
    }

    public String getWardName() {
        return wardName;
    }

    public void setWardName(String wardName) {
        this.wardName = wardName;
    }

    public long getAssignedBedId() {
        return assignedBedId;
    }

    public void setAssignedBedId(long assignedBedId) {
        this.assignedBedId = assignedBedId;
    }

    public LocalDateTime getReservedAt() {
        return reservedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = Objects.requireNonNull(status);
    }

    public String getReroutedFromHospital() {
        return reroutedFromHospital;
    }

    public void setReroutedFromHospital(String reroutedFromHospital) {
        this.reroutedFromHospital = reroutedFromHospital;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AmbulanceReservation that)) return false;
        return reservationId != 0 && reservationId == that.reservationId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(reservationId);
    }

    @Override
    public String toString() {
        return String.format("AmbulanceReservation[id=%d, amb=%s (%s), bed=#%d @ %s/%s, status=%s, expires=%s]",
                reservationId, ambulanceId, ambulanceType, assignedBedId, hospitalName, wardName, status, expiresAt);
    }
}
