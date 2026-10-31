package com.group15.hospital.model.ambulance;

import com.group15.hospital.model.enums.UserRole;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain model representing an instant system pop-up alert delivered to clinical/triage terminals
 * (SRS F13, Use Cases U5 & U6).
 * <p>
 * Alerts notify staff of high-priority operational state changes without manual screen refreshing:
 * <ul>
 *   <li>{@code BED_CLEANED}: Triggered when sanitization staff finishes cleaning a room (U6).</li>
 *   <li>{@code AMBULANCE_RESERVED}: Triggered when an incoming ambulance claims a bed (U5).</li>
 *   <li>{@code AMBULANCE_REROUTED}: Triggered when an en-route ambulance is redirected due to saturation (U5).</li>
 *   <li>{@code NO_CAPACITY_ALERT}: Critical warning when no hospitals have compatible critical beds.</li>
 *   <li>{@code RESERVATION_EXPIRED}: Triggered when a 15-minute ambulance hold window elapses.</li>
 *   <li>{@code WAITLIST_READY}: Notification indicating a freshly cleaned bed matches a waitlisted patient.</li>
 * </ul>
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class SystemAlert {

    public static final String TYPE_BED_CLEANED = "BED_CLEANED";
    public static final String TYPE_AMBULANCE_RESERVED = "AMBULANCE_RESERVED";
    public static final String TYPE_AMBULANCE_REROUTED = "AMBULANCE_REROUTED";
    public static final String TYPE_NO_CAPACITY = "NO_CAPACITY_ALERT";
    public static final String TYPE_RESERVATION_EXPIRED = "RESERVATION_EXPIRED";
    public static final String TYPE_WAITLIST_READY = "WAITLIST_READY";

    private final long alertId;
    private final String alertType;
    private final UserRole targetRole;
    private final String hospitalName;
    private final Long bedId;
    private final String message;
    private final LocalDateTime timestamp;
    private boolean acknowledged;

    /**
     * Full constructor including persistent alert ID (for DAO and offline queue retrieval).
     */
    public SystemAlert(
            long alertId,
            String alertType,
            UserRole targetRole,
            String hospitalName,
            Long bedId,
            String message,
            LocalDateTime timestamp,
            boolean acknowledged
    ) {
        this.alertId = alertId;
        this.alertType = Objects.requireNonNull(alertType, "alertType must not be null");
        this.targetRole = (targetRole != null) ? targetRole : UserRole.NURSE;
        this.hospitalName = Objects.requireNonNull(hospitalName, "hospitalName must not be null");
        this.bedId = bedId;
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.timestamp = (timestamp != null) ? timestamp : LocalDateTime.now();
        this.acknowledged = acknowledged;
    }

    /**
     * Convenience constructor for creating new in-flight alerts before persistence.
     */
    public SystemAlert(
            String alertType,
            UserRole targetRole,
            String hospitalName,
            Long bedId,
            String message
    ) {
        this(0L, alertType, targetRole, hospitalName, bedId, message, LocalDateTime.now(), false);
    }

    // ── Getters and Setters ──

    public long getAlertId() {
        return alertId;
    }

    public String getAlertType() {
        return alertType;
    }

    public UserRole getTargetRole() {
        return targetRole;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public Long getBedId() {
        return bedId;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SystemAlert that)) return false;
        return alertId != 0 && alertId == that.alertId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(alertId);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s Alert for %s @ %s (Bed #%s): %s",
                timestamp, alertType, targetRole, hospitalName, (bedId != null ? bedId : "N/A"), message);
    }
}
