package com.rousoftware.wynngatheringstats.stats;

public enum CraftingProfession {
    ALCHEMISM("Alchemism"),
    ARMOURING("Armouring"),
    COOKING("Cooking"),
    JEWELING("Jeweling"),
    SCRIBING("Scribing"),
    TAILORING("Tailoring"),
    WEAPONSMITHING("Weaponsmithing"),
    WOODWORKING("Woodworking");

    private final String displayName;

    CraftingProfession(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
