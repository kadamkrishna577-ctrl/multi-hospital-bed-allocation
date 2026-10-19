package com.group15.hospital.model.hierarchy;

import java.time.LocalDateTime;

// final becuase no other file should be able to do anything to 
// these log entry workings 
public final class AuditLogEntry {

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

        if(timestamp!=null){
            this.timestamp = timestamp;
        }
        else this.timestamp = LocalDateTime.now();

        this.actorUsername = actorUsername;
        this.actionType = actionType;
        this.entityId = entityId;
        this.details = details;
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

    @Override
    public String toString() {
        return String.format(
                "[%s] User: %s | Action: %s | Entity: %s | Details: %s",
                timestamp, actorUsername, actionType, entityId, details
        );
    }
}
