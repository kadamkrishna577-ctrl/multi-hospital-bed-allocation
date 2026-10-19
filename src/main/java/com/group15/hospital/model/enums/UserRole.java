package com.group15.hospital.model.enums;

public enum UserRole {
    // to hide and show parts of ui panels.
    ADMIN("administrator", true),
    DOCTOR("physician", true),
    NURSE("nurse", true),
    PARAMEDIC("ambulance_dispatcher", false),
    CLEANING_STAFF("sanitization", false);

    private final String displayName;
    private final boolean administrativeAccess;

    UserRole(String displayName, boolean administrativeAccess) {
        this.displayName = displayName;
        this.administrativeAccess = administrativeAccess;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean hasAdministrativeAccess() {
        return administrativeAccess;
    }

    public static UserRole fromDatabaseValue(String value) {
        // safest default role so that the program doesnt crash...
        if (value == null || value.isBlank()) {
            return NURSE;
        }
        return UserRole.valueOf(value.trim().toUpperCase());
    }
}
