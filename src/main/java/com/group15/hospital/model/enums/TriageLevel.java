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

    // level 1 emergency can preempt level 4 or level 5 patients...
    public boolean canSwap(TriageLevel occupantLevel) {
        if (occupantLevel == null) {
            return false;
        }
        return this == LEVEL_1 && occupantLevel.priorityScore >= LEVEL_4.priorityScore;
    }

    // parser for the MYSQl database
    // when the database gives the triageLevel
    // this will parse it safely to the TriageLevel enum...
    public static TriageLevel parseDatabase(String value) {
        if (value == null || value.isBlank()) {
            return LEVEL_5;
        }

        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");

        // if a pure digit is passed like 1, 2, 3...
        if (normalized.matches("^[1-5]$")) {
            normalized = "LEVEL_" + normalized;
        }

        // check if passed as descriptive name like CRITICAL or EMERGENCY...
        for (TriageLevel level : values()) {
            if (level.name().equalsIgnoreCase(normalized) ||
                level.description.equalsIgnoreCase(normalized) ||
                level.description.replace("-", "_").equalsIgnoreCase(normalized)) {
                return level;
            }
        }

        try {
            return TriageLevel.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // fallback to non-urgent level 5 so the system never crashes...
            return LEVEL_5;
        }
    }
}

