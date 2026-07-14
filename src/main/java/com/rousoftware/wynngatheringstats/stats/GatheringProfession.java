package com.rousoftware.wynngatheringstats.stats;

public enum GatheringProfession {
    WOODCUTTING("Woodcutting"),
    MINING("Mining"),
    FISHING("Fishing"),
    FARMING("Farming");

    private final String displayName;

    GatheringProfession(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
