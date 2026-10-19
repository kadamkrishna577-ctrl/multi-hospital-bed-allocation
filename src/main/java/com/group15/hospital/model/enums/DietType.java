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

    public static DietType fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            // default to regular..
            return REGULAR;
        }
        return DietType.valueOf(value.trim().toUpperCase());
    }
}
