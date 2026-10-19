package com.group15.hospital.model.hierarchy;

import java.time.LocalDateTime;
import java.util.Objects;

// final becuase no other file should be able to do anything to 
// these log entry workings 
// implements Comparable so audit logs can easily be sorted by latest timestamp...


// added comparable so that the custom sort function can be made
// and Collections.sort(loglist) can sort normally
// no need to write custom sort functions...
public final class AuditLogEntry implements Comparable<AuditLogEntry> {

    private final long logId;
    private final LocalDateTime timestamp;
    private final String actorUsername;
    private final String actionType;
    private final String entityId;
    private final String details;


    public AuditLogEntry(
            long logId,
            LocalDateTime timestamp,
            String actorUsername,
            String actionType,
            String entityId,
            String details
    ) {
        this.logId = logId;

        if (timestamp != null) {
            this.timestamp = timestamp;
        } else {
            this.timestamp = LocalDateTime.now();
        }

        // null safe string defaults so nothing prints null...
        this.actorUsername = (actorUsername != null) ? actorUsername : "system";
        this.actionType = (actionType != null) ? actionType : "UNKNOWN";
        this.entityId = (entityId != null) ? entityId : "";
        this.details = (details != null) ? details : "";
    }

    // if there are logs that havent been given an ID yet
    public AuditLogEntry(String actorUsername, String actionType, String entityId, String details) {
        this(0, LocalDateTime.now(), actorUsername, actionType, entityId, details);
    }


    public long getLogId() {
        return logId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActionType() {
        return actionType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDetails() {
        return details;
    }

    // sort newest logs first...
    @Override
    public int compareTo(AuditLogEntry other) {
        if (other == null || other.timestamp == null) {
            return -1;
        }
        if (this.timestamp == null) {
            return 1;
        }
        return other.timestamp.compareTo(this.timestamp);
    }
    
    // have to override the equals as otherwise
    // it will check if the pointer is potining to the same data...
    // equals and hashcode so it works properly in lists, sets and test comparisons...
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditLogEntry that = (AuditLogEntry) o;
        return logId == that.logId &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(actorUsername, that.actorUsername) &&
                Objects.equals(actionType, that.actionType) &&
                Objects.equals(entityId, that.entityId) &&
                Objects.equals(details, that.details);
    }

    // 
    @Override
    public int hashCode() {
        return Objects.hash(logId, timestamp, actorUsername, actionType, entityId, details);
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] User: %s | Action: %s | Entity: %s | Details: %s",
                timestamp, actorUsername, actionType, entityId, details
        );
    }
}

