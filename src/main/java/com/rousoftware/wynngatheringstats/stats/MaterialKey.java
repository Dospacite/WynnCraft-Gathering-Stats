package com.rousoftware.wynngatheringstats.stats;

public record MaterialKey(String name, int tier) {
    public MaterialKey {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("material name is required");
        }
        if (tier < 1 || tier > 3) {
            throw new IllegalArgumentException("material tier must be between 1 and 3");
        }
    }
}
