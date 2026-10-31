package com.group15.hospital.model.enums;

public enum UserRole {
    // to hide and show parts of ui panels...
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

    // parser from database strings
    // handles user role strings safely...
    public static UserRole fromDatabaseValue(String value) {
        // safest default role so that the program doesnt crash...
        if (value == null || value.isBlank()) {
            return NURSE;
        }

        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");

        if (normalized.equals("ADMINISTRATOR")) {
            return ADMIN;
        }
        if (normalized.equals("PHYSICIAN")) {
            return DOCTOR;
        }
        if (normalized.equals("AMBULANCE_DISPATCHER") || normalized.equals("EMS") || normalized.equals("DISPATCHER")) {
            return PARAMEDIC;
        }
        if (normalized.equals("SANITIZATION") || normalized.equals("CLEANING") || normalized.equals("JANITOR")) {
            return CLEANING_STAFF;
        }

        try {
            return UserRole.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // fallback safe role...
            return NURSE;
        }
    }

    // alias so teammates calling parseDatabase also work seamlessly...
    public static UserRole parseDatabase(String value) {
        return fromDatabaseValue(value);
    }
}

