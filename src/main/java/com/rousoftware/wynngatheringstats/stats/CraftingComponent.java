package com.rousoftware.wynngatheringstats.stats;

public record CraftingComponent(String name, int tier, int amount, CraftingComponentKind kind) {
    public CraftingComponent {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("component name is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("component kind is required");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("component amount must be positive");
        }
        if (kind == CraftingComponentKind.MATERIAL && (tier < 1 || tier > 3)) {
            throw new IllegalArgumentException("material tier must be between 1 and 3");
        }
        if (kind == CraftingComponentKind.INGREDIENT && tier != 0) {
            throw new IllegalArgumentException("ingredients do not have a material tier");
        }

        name = name.strip().replaceAll("\\s+", " ");
    }

    public int amountPerCraft(boolean professionSpeedBombActive) {
        if (kind != CraftingComponentKind.MATERIAL || !professionSpeedBombActive) {
            return amount;
        }
        return amount / 2;
    }
}
