package com.group15.hospital.model.enums;

public enum EquipmentType {
    VENTILATOR("ventilator", 150.00, 500),
    OXYGEN("oxygen", 40.0, 1000),
    DIALYSIS("dialysis", 200.0, 300),
    MONITOR("cardiac_monitor", 60.0, 1500);

    // fields for cost and hourly usage limits...
    public final String displayName;
    public final double hourlyCost;
    public final int safeOperatingHours;

    EquipmentType(String displayName, double hourlyCost, int safeOperatingHours) {
        this.displayName = displayName;
        this.hourlyCost = hourlyCost;
        this.safeOperatingHours = safeOperatingHours;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getHourlyCost() {
        return hourlyCost;
    }

    public int getSafeOperatingHours() {
        return safeOperatingHours;
    }

    // parser for the database values
    // handles variations like cardiac_monitor or common typos so teammates dont crash...
    public static EquipmentType parseDatabase(String value) {
        if (value == null || value.isBlank()) {
            // here we throw the error because 
            // we cannot set anything here to default
            // so that billing can be done!
            throw new IllegalArgumentException("EquipmentType database value cannot be null or empty.");
        }

        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");

        // handling aliases and accidental typos for ventilator and monitor...
        if (normalized.equals("VENTIALTOR") || normalized.equals("VENTILATOR")) {
            return VENTILATOR;
        }
        if (normalized.equals("CARDIAC_MONITOR") || normalized.equals("CARDIAC") || normalized.equals("MONITOR")) {
            return MONITOR;
        }

        try {
            return EquipmentType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown EquipmentType database value: " + value, e);
        }
    }
}

