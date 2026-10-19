package com.group15.hospital.model.enums;

public enum BedStatus {
    VACANT("vacant", true),
    OCCUPIED("occupied", false),
    RESERVED_EN_ROUTE("reserved_en_route", false),
    REQUIRES_SANITIZATION("req_sanitization", false),
    RESTRICTED_BUFFER("restricted_buffer", false);

    // kept as aliases so any old code or tests using these wont break...

    private final String displayName;
    private final boolean availableForAllocation;

    BedStatus(String displayName, boolean availableForAllocation) {
        this.displayName = displayName;
        this.availableForAllocation = availableForAllocation;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAvailableForAllocation() {
        return availableForAllocation;
    }

    // parser for mysql database values
    // supports both short forms and full state pattern names...
    public static BedStatus parseDatabase(String value) {
        if (value == null || value.isBlank()) {
            // default to vacant so it doesnt crash...
            return VACANT;
        }

        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");

        // map aliases to primary states
        if (normalized.equals("RESERVED")
                || normalized.equals("RESERVED_EN_ROUTE")
                || normalized.equals("EN_ROUTE")) {
            return RESERVED_EN_ROUTE;
        }
        if (normalized.equals("SANITIZATION")
                || normalized.equals("REQ_SANITIZATION")
                || normalized.equals("REQUIRES_SANITIZATION")
                || normalized.equals("DIRTY")) {
            return REQUIRES_SANITIZATION;
        }
        if (normalized.equals("RESTRICTED")
                || normalized.equals("RESTRICTED_FOR_ISOLATION")
                || normalized.equals("RESTRICTED_BUFFER")
                || normalized.equals("BUFFER")) {
            return RESTRICTED_BUFFER;
        }

        try {
            return BedStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // fallback safe return rather than breaking the application...
            return VACANT;
        }
    }

    // kept for backwards compatibility if teammates already used the old spelling...
    public static BedStatus parseDatabse(String value) {
        return parseDatabase(value);
    }
}
