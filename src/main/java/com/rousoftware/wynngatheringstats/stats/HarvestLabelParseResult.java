package com.rousoftware.wynngatheringstats.stats;

import java.util.Optional;
import java.util.OptionalInt;

/** A diagnostic-friendly representation of the data read from one harvest label. */
public record HarvestLabelParseResult(
        OptionalInt amount, Optional<String> materialName, OptionalInt tier) {
    public boolean isComplete() {
        return amount.isPresent() && materialName.isPresent() && tier.isPresent();
    }

    public boolean hasAmountAndMaterial() {
        return amount.isPresent() && materialName.isPresent();
    }

    public String failureReason() {
        if (amount.isEmpty()) {
            return "amount was not found";
        }
        if (materialName.isEmpty()) {
            return "material name was not found";
        }
        if (tier.isEmpty()) {
            return "tier formatting was not found";
        }
        return "none";
    }
}
