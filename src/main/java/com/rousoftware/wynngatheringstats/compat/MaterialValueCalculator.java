package com.rousoftware.wynngatheringstats.compat;

import com.rousoftware.wynngatheringstats.stats.MaterialKey;
import java.util.Map;
import java.util.OptionalDouble;

public final class MaterialValueCalculator {
    private static final double EMERALDS_PER_LIQUID_EMERALD = 4_096d;

    private MaterialValueCalculator() {}

    public static OptionalDouble liquidEmeraldsPerHour(
            Map<MaterialKey, Double> rates,
            MaterialPriceProvider priceProvider,
            MaterialPriceMode priceMode) {
        if (!priceProvider.isAvailable() || rates.isEmpty()) {
            return OptionalDouble.empty();
        }

        double emeraldsPerHour = 0;
        boolean hasMarketData = false;
        for (Map.Entry<MaterialKey, Double> entry : rates.entrySet()) {
            OptionalDouble unitPrice = priceProvider.getUnitPrice(
                    entry.getKey().name(), entry.getKey().tier(), priceMode);
            if (unitPrice.isEmpty()) {
                continue;
            }
            emeraldsPerHour += entry.getValue() * unitPrice.getAsDouble();
            hasMarketData = true;
        }

        return hasMarketData
                ? OptionalDouble.of(emeraldsPerHour / EMERALDS_PER_LIQUID_EMERALD)
                : OptionalDouble.empty();
    }
}
