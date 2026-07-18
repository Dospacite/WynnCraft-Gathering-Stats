package com.rousoftware.wynngatheringstats.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record CraftingRecipe(List<CraftingComponent> materials, List<CraftingComponent> ingredients) {
    private static final Comparator<CraftingComponent> COMPONENT_ORDER = Comparator.comparing(
                    CraftingComponent::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(CraftingComponent::tier)
            .thenComparingInt(CraftingComponent::amount);

    public CraftingRecipe {
        if (materials == null || ingredients == null) {
            throw new IllegalArgumentException("recipe components are required");
        }
        if (materials.stream().anyMatch(component -> component.kind() != CraftingComponentKind.MATERIAL)) {
            throw new IllegalArgumentException("the material list can only contain materials");
        }
        if (ingredients.stream().anyMatch(component -> component.kind() != CraftingComponentKind.INGREDIENT)) {
            throw new IllegalArgumentException("the ingredient list can only contain ingredients");
        }

        materials = sortedCopy(materials);
        ingredients = sortedCopy(ingredients);
    }

    public List<CraftingComponent> components() {
        List<CraftingComponent> components = new ArrayList<>(materials.size() + ingredients.size());
        components.addAll(materials);
        components.addAll(ingredients);
        return List.copyOf(components);
    }

    public boolean hasSameInputsAs(CraftingRecipe other) {
        if (other == null || materials.size() != other.materials.size()) {
            return false;
        }
        for (int index = 0; index < materials.size(); index++) {
            CraftingComponent left = materials.get(index);
            CraftingComponent right = other.materials.get(index);
            if (!left.name().equalsIgnoreCase(right.name()) || left.tier() != right.tier()) {
                return false;
            }
        }
        return ingredients.equals(other.ingredients);
    }

    private static List<CraftingComponent> sortedCopy(List<CraftingComponent> components) {
        return components.stream().sorted(COMPONENT_ORDER).toList();
    }
}
