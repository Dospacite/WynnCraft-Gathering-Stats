package com.rousoftware.wynngatheringstats.compat;

import com.rousoftware.wynngatheringstats.GatheringStatsClient;
import net.fabricmc.loader.api.FabricLoader;

public final class MaterialPriceProviders {
    private MaterialPriceProviders() {}

    public static MaterialPriceProvider create() {
        if (!FabricLoader.getInstance().isModLoaded("wynnventory")) {
            return MaterialPriceProvider.UNAVAILABLE;
        }

        try {
            Class<?> providerClass =
                    Class.forName("com.rousoftware.wynngatheringstats.compat.WynnventoryPriceProvider");
            return (MaterialPriceProvider) providerClass.getDeclaredConstructor().newInstance();
        } catch (LinkageError | ReflectiveOperationException | RuntimeException exception) {
            GatheringStatsClient.LOGGER.error("WynnVentory is installed but its price API is unavailable", exception);
            return MaterialPriceProvider.UNAVAILABLE;
        }
    }
}
