package com.group15.hospital.model.enums;

public enum TriageLevel {
    LEVEL_1(1, "CRITICAL"),
    LEVEL_2(2, "EMERGENCY"),
    LEVEL_3(3, "URGENT"),
    LEVEL_4(4, "LESS-URGENT"),
    LEVEL_5(5, "NON-URGENT");

    private final int priorityScore;
    private final String description;

    TriageLevel(int priorityScore, String description) {
        this.priorityScore = priorityScore;
        this.description = description;
    }

    public int getPriorityScore() {
        return priorityScore;
    }

    public String getDescription() {
        return description;
    }

    public boolean canSwap(TriageLevel occupantLevel) {
        return this == LEVEL_1 && occupantLevel.priorityScore >= LEVEL_4.priorityScore;
    }

    // parser for the MYSQl database
    // when the database gives the triageLevel
    // this will parse it safely to the TriageLevel enum...
    public static TriageLevel parseDatabase(String value){
        if(value == null || value.isBlank()){
            return LEVEL_5;
        }

        String normalized = value.trim().toUpperCase();

        if(normalized.matches("^[1-5]$")) {
            normalized = "LEVEL_" + normalized;
        }

        return TriageLevel.valueOf(normalized);
    }
}
