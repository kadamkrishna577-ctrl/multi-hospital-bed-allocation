package com.group15.hospital.model.enums;

public enum DietType {

    REGULAR("standard_diet", 0.0),
    LIQUID("liquid_diet", 15.0),
    IV_FLUIDS("iv_nutrition", 45.0);

    private final String displayName;
    private final double supplementCost;

    DietType(String displayName, double supplementCost) {
        this.displayName = displayName;
        this.supplementCost = supplementCost;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getSupplementCost() {
        return supplementCost;
    }

    // parser from database strings
    // handles both enum names and descriptions like iv_nutrition or regular...
    public static DietType fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            // default to regular..
            return REGULAR;
        }

        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");

        if (normalized.equals("STANDARD_DIET") || normalized.equals("STANDARD") || normalized.equals("REGULAR_DIET")) {
            return REGULAR;
        }
        if (normalized.equals("LIQUID_DIET")) {
            return LIQUID;
        }
        if (normalized.equals("IV_NUTRITION") || normalized.equals("IV") || normalized.equals("IV_FLUID")) {
            return IV_FLUIDS;
        }

        try {
            return DietType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // default fallback so meal orders never crash...
            return REGULAR;
        }
    }

    // alias so that teammates calling parseDatabase also work seamlessly...
    public static DietType parseDatabase(String value) {
        return fromDatabaseValue(value);
    }
}

