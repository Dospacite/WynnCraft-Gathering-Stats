package com.rousoftware.wynngatheringstats.mixin;

import com.rousoftware.wynngatheringstats.event.CombatItemPickupEvent;
import com.wynntils.core.WynntilsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(
            method = "handleTakeItemEntity(Lnet/minecraft/network/protocol/game/ClientboundTakeItemEntityPacket;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)Lnet/minecraft/world/entity/Entity;",
                            ordinal = 0,
                            shift = At.Shift.BEFORE))
    private void gatheringStats$onItemPickup(ClientboundTakeItemEntityPacket packet, CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || packet.getPlayerId() != minecraft.player.getId()
                || packet.getAmount() <= 0) {
            return;
        }

        Entity entity = minecraft.level.getEntity(packet.getItemId());
        if (!(entity instanceof ItemEntity itemEntity)) {
            return;
        }

        ItemStack itemStack = itemEntity.getItem();
        if (!itemStack.isEmpty()) {
            WynntilsMod.postEvent(new CombatItemPickupEvent(itemStack, packet.getAmount()));
        }
    }
}
