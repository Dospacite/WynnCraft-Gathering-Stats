package com.rousoftware.wynngatheringstats.mixin;

import com.wynntils.core.consumers.features.Feature;
import com.wynntils.core.consumers.features.FeatureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FeatureManager.class)
public interface FeatureManagerInvoker {
    @Invoker("registerFeature")
    void gatheringStats$registerFeature(Feature feature);
}
