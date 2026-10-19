package com.group15.hospital.model.enums;

public enum BedStatus {
    VACANT("vacant", true),
    OCCUPIED("occupied", false),
    RESERVED("reserved", false),
    SANITIZATION("req_sanitization", false),
    RESTRICTED("restricted_for_isolation", false);

    private final String displayName;
    private final boolean availableForAllocation;

    BedStatus(String displayName, boolean availableForAllocation){
        this.displayName = displayName;
        this.availableForAllocation = availableForAllocation;
    }

    public String getDisplayName(){
        return displayName;
    }

    public  boolean isAvailableForAllocation(){
        return availableForAllocation;
    }

    public static BedStatus parseDatabse(String value){
        if(value == null || value.isBlank()){
            return VACANT;
        }
        return BedStatus.valueOf(value.trim().toUpperCase());
    }
}
