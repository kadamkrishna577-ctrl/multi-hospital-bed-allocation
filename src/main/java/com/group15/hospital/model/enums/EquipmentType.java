package com.group15.hospital.model.enums;

public enum EquipmentType {
    VENTIALTOR("ventilator", 150.00, 500),
    OXYGEN("oxygen", 40.0, 1000),
    DIALYSIS("dialysis",200.0, 300),
    MONITOR("cardiac_monitor", 60.0, 1500);

    public final String displayName;
    public final double hourlyCost;
    public final int safeOperatingHours;

    EquipmentType(String displayName, double hourlyCost, int safeOperatingHours){
        this.displayName = displayName;
        this.hourlyCost = hourlyCost;
        this.safeOperatingHours = safeOperatingHours;
    }

    public String getDisplayName(){
        return displayName;
    }

    public double getHourlyCost(){
        return hourlyCost;
    }

    public int getSafeOperatingHours(){
        return safeOperatingHours;
    }

    public static  EquipmentType parseDatabase(String value){
        if(value == null || value.isBlank()){
            // here we throw the error because 
            // we cannot set anything here to default
            // so that billing can be done!
            throw new IllegalArgumentException("EquipmentType database value cannot be null or empty.");
        }
        return EquipmentType.valueOf(value.trim().toUpperCase());
    }
}
