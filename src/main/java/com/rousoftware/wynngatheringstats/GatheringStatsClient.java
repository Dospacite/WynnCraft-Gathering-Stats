package com.rousoftware.wynngatheringstats;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GatheringStatsClient implements ClientModInitializer {
    public static final String MOD_ID = "wynngatheringstats";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("WynnCraft Gathering Stats initialized (Wynntils 4.2.2+ supported)");
    }
}
