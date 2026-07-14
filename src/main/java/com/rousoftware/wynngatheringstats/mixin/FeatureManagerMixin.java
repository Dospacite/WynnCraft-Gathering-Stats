package com.rousoftware.wynngatheringstats.mixin;

import com.wynntils.core.consumers.features.FeatureManager;
import com.rousoftware.wynngatheringstats.feature.GatheringStatsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureManager.class)
public abstract class FeatureManagerMixin {
    private static boolean gatheringStats$registered;

    @Inject(method = "init", at = @At("TAIL"))
    private void gatheringStats$registerFeature(CallbackInfo callbackInfo) {
        if (gatheringStats$registered) {
            return;
        }

        ((FeatureManagerInvoker) (Object) this).gatheringStats$registerFeature(new GatheringStatsFeature());
        gatheringStats$registered = true;
    }
}
