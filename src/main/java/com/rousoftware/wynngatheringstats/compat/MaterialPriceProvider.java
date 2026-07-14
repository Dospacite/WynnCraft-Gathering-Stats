package com.rousoftware.wynngatheringstats.compat;

import java.util.OptionalDouble;

public interface MaterialPriceProvider {
    MaterialPriceProvider UNAVAILABLE = new MaterialPriceProvider() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public OptionalDouble getUnitPrice(String materialName, int tier, MaterialPriceMode priceMode) {
            return OptionalDouble.empty();
        }
    };

    boolean isAvailable();

    OptionalDouble getUnitPrice(String materialName, int tier, MaterialPriceMode priceMode);
}
