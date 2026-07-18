package com.rousoftware.wynngatheringstats.event;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

public final class CombatItemPickupEvent extends Event {
    private final ItemStack itemStack;
    private final int amount;

    public CombatItemPickupEvent(ItemStack itemStack, int amount) {
        this.itemStack = itemStack;
        this.amount = amount;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public int getAmount() {
        return amount;
    }
}
