package com.rousoftware.wynngatheringstats.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rousoftware.wynngatheringstats.stats.MaterialKey;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class MaterialValueCalculatorTest {
    @Test
    void ignoresMaterialsWhoseMarketDataIsUnavailable() {
        Map<MaterialKey, Double> rates = Map.of(
                new MaterialKey("Unavailable Ingot", 1), 100d,
                new MaterialKey("Priced Ingot", 2), 20d,
                new MaterialKey("Priced Ingot", 3), 5d);
        MaterialPriceProvider prices = provider(Map.of(
                new MaterialKey("Priced Ingot", 2), 409.6d,
                new MaterialKey("Priced Ingot", 3), 819.2d));

        OptionalDouble result = MaterialValueCalculator.liquidEmeraldsPerHour(
                rates, prices, MaterialPriceMode.LOWEST);

        assertEquals(3d, result.orElseThrow(), 0.0001);
    }

    @Test
    void remainsUnavailableWhenNoMaterialHasMarketData() {
        OptionalDouble result = MaterialValueCalculator.liquidEmeraldsPerHour(
                Map.of(new MaterialKey("Unavailable Ingot", 1), 100d),
                provider(Map.of()),
                MaterialPriceMode.LOWEST);

        assertTrue(result.isEmpty());
    }

    private MaterialPriceProvider provider(Map<MaterialKey, Double> prices) {
        return new MaterialPriceProvider() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public OptionalDouble getUnitPrice(String materialName, int tier, MaterialPriceMode priceMode) {
                Double price = prices.get(new MaterialKey(materialName, tier));
                return price == null ? OptionalDouble.empty() : OptionalDouble.of(price);
            }
        };
    }
}
