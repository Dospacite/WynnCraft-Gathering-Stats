package com.rousoftware.wynngatheringstats.compat;

import com.wynnventory.api.service.TrademarketService;
import com.wynnventory.model.item.trademarket.PriceType;
import com.wynnventory.model.item.trademarket.TrademarketItemSnapshot;
import java.util.OptionalDouble;

final class WynnventoryPriceProvider implements MaterialPriceProvider {
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public OptionalDouble getUnitPrice(String materialName, int tier, MaterialPriceMode priceMode) {
        TrademarketItemSnapshot snapshot = TrademarketService.INSTANCE.getItem(materialName, tier);
        if (snapshot == null || snapshot.live() == null) {
            return OptionalDouble.empty();
        }

        PriceType priceType = switch (priceMode) {
            case LOWEST -> PriceType.LOWEST;
            case MOVING_MEDIAN -> PriceType.MOVING_MEDIAN;
            case TRIMMED_AVERAGE -> PriceType.AVG_80;
            case AVERAGE -> PriceType.AVG;
        };
        Double price = priceType.getValue(snapshot.live());
        if (price == null || !Double.isFinite(price) || price < 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(price);
    }
}
