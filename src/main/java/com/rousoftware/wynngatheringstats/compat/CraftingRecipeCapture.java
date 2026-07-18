package com.rousoftware.wynngatheringstats.compat;

import com.rousoftware.wynngatheringstats.stats.CraftingComponent;
import com.rousoftware.wynngatheringstats.stats.CraftingComponentKind;
import com.rousoftware.wynngatheringstats.stats.CraftingProfession;
import com.rousoftware.wynngatheringstats.stats.CraftingRecipe;
import com.wynntils.core.components.Models;
import com.wynntils.models.items.items.game.IngredientItem;
import com.wynntils.models.items.items.game.MaterialItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Captures the station inputs immediately before a craft and compares them with the consumed stacks. */
public final class CraftingRecipeCapture {
    private static final int CRAFTING_STATION_SLOT_COUNT = 54;
    private static final long CAPTURE_TIMEOUT_NANOS = 120_000_000_000L;
    private static final Comparator<ComponentId> COMPONENT_ORDER = Comparator.comparing(
                    (ComponentId component) -> component.kind().ordinal())
            .thenComparing(ComponentId::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(ComponentId::tier);

    private PendingCapture pendingCapture;

    public synchronized void captureBeforeClick(
            CraftingProfession profession,
            AbstractContainerMenu menu,
            boolean professionSpeedBombActive,
            long nowNanos) {
        if (profession == null || menu == null) {
            return;
        }
        Map<ComponentId, Integer> selectedComponents = scanInputs(menu, CRAFTING_STATION_SLOT_COUNT);
        long materialTypes = selectedComponents.keySet().stream()
                .filter(component -> component.kind() == CraftingComponentKind.MATERIAL)
                .count();
        if (materialTypes < 2) {
            return;
        }
        pendingCapture = new PendingCapture(
                profession,
                Set.copyOf(selectedComponents.keySet()),
                scanInputs(menu, menu.getItems().size()),
                professionSpeedBombActive,
                nowNanos);
    }

    public synchronized Optional<CraftingRecipe> completeCraft(
            CraftingProfession profession,
            AbstractContainerMenu menu,
            long nowNanos) {
        PendingCapture pending = pendingCapture;
        if (pending == null
                || profession == null
                || menu == null
                || pending.profession() != profession
                || nowNanos < pending.capturedAtNanos()
                || nowNanos - pending.capturedAtNanos() > CAPTURE_TIMEOUT_NANOS) {
            pendingCapture = null;
            return Optional.empty();
        }

        Map<ComponentId, Integer> remaining = scanInputs(menu, menu.getItems().size());
        List<CraftingComponent> materials = new ArrayList<>();
        List<CraftingComponent> ingredients = new ArrayList<>();
        pending.selectedComponents().stream().sorted(COMPONENT_ORDER).forEach(id -> {
                    int consumed = pending.totalComponents().getOrDefault(id, 0)
                            - remaining.getOrDefault(id, 0);
                    if (consumed <= 0) {
                        return;
                    }
                    int normalAmount = id.kind() == CraftingComponentKind.MATERIAL
                                    && pending.professionSpeedBombActive()
                            ? saturatingDouble(consumed)
                            : consumed;
                    CraftingComponent component =
                            new CraftingComponent(id.name(), id.tier(), normalAmount, id.kind());
                    if (id.kind() == CraftingComponentKind.MATERIAL) {
                        materials.add(component);
                    } else {
                        ingredients.add(component);
                    }
                });

        if (materials.size() < 2) {
            return Optional.empty();
        }
        pendingCapture = null;
        return Optional.of(new CraftingRecipe(materials, ingredients));
    }

    public synchronized void reset() {
        pendingCapture = null;
    }

    private static Map<ComponentId, Integer> scanInputs(AbstractContainerMenu menu, int requestedEnd) {
        Map<ComponentId, Integer> components = new HashMap<>();
        List<ItemStack> items = menu.getItems();
        int end = Math.min(requestedEnd, items.size());
        for (int slot = 0; slot < end; slot++) {
            ItemStack stack = items.get(slot);
            if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
                continue;
            }

            MaterialItem material = Models.Item.asWynnItem(stack, MaterialItem.class).orElse(null);
            if (material != null) {
                ComponentId id = new ComponentId(
                        material.getMaterialInfo().name(),
                        material.getQualityTier(),
                        CraftingComponentKind.MATERIAL);
                components.merge(id, stack.getCount(), Integer::sum);
                continue;
            }

            IngredientItem ingredient = Models.Item.asWynnItem(stack, IngredientItem.class).orElse(null);
            if (ingredient != null) {
                ComponentId id = new ComponentId(ingredient.getName(), 0, CraftingComponentKind.INGREDIENT);
                components.merge(id, stack.getCount(), Integer::sum);
            }
        }
        return Map.copyOf(components);
    }

    private static int saturatingDouble(int value) {
        return value > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : value * 2;
    }

    private record ComponentId(String name, int tier, CraftingComponentKind kind) {}

    private record PendingCapture(
            CraftingProfession profession,
            Set<ComponentId> selectedComponents,
            Map<ComponentId, Integer> totalComponents,
            boolean professionSpeedBombActive,
            long capturedAtNanos) {}
}
