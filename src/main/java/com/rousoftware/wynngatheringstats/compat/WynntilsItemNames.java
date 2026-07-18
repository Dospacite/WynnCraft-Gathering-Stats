package com.rousoftware.wynngatheringstats.compat;

import com.rousoftware.wynngatheringstats.GatheringStatsClient;
import com.wynntils.core.components.Models;
import com.wynntils.core.text.StyledText;
import com.wynntils.models.items.properties.NamedItemProperty;
import com.wynntils.utils.mc.McUtils;
import com.wynntils.utils.wynn.WynnUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.world.item.ItemStack;

/** Builds item-name suggestions from Wynntils' live models and item annotations. */
public final class WynntilsItemNames {
    private final Set<String> observedNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public Optional<String> observe(ItemStack itemStack) {
        Optional<String> name = itemName(itemStack);
        name.ifPresent(observedNames::add);
        return name;
    }

    public Optional<String> canonicalName(String requestedName) {
        String normalized = normalize(requestedName);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return knownNames().stream().filter(name -> name.equalsIgnoreCase(normalized)).findFirst();
    }

    public Collection<String> knownNames() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(observedNames);
        observeInventory();
        names.addAll(observedNames);

        try {
            Models.Gear.getAllGearInfos().map(info -> info.name()).forEach(names::add);
            Models.Ingredient.getAllIngredientInfos().map(info -> info.name()).forEach(names::add);
            Models.Rewards.getAllCharmInfos().map(info -> info.name()).forEach(names::add);
            Models.Rewards.getAllTomeInfos().map(info -> info.name()).forEach(names::add);
            Models.Aspect.getAllAspectInfos().map(info -> info.name()).forEach(names::add);
        } catch (RuntimeException exception) {
            GatheringStatsClient.LOGGER.debug("Wynntils item registries are not ready for suggestions yet", exception);
        }

        return List.copyOf(names);
    }

    public static boolean matches(String trackedName, String observedName) {
        return normalize(trackedName).equalsIgnoreCase(normalize(observedName));
    }

    private Optional<String> itemName(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return Optional.empty();
        }

        String name = Models.Item.asWynnItemProperty(itemStack, NamedItemProperty.class)
                .map(NamedItemProperty::getName)
                .orElseGet(() -> StyledText.fromComponent(itemStack.getHoverName())
                        .getNormalized()
                        .trim()
                        .getStringWithoutFormatting());
        name = normalize(name);
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    private void observeInventory() {
        try {
            if (McUtils.player() == null) {
                return;
            }
            for (int slot = 0; slot < McUtils.inventory().getContainerSize(); slot++) {
                observe(McUtils.inventory().getItem(slot));
            }
        } catch (RuntimeException exception) {
            GatheringStatsClient.LOGGER.debug("Could not collect item suggestions from the inventory", exception);
        }
    }

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return WynnUtils.stripItemNameMarkers(name).strip().replaceAll("\\s+", " ");
    }
}
