package com.dex.api;

import net.minecraft.world.item.ItemStack;
import java.util.Collections;
import java.util.List;

/**
 * Represents a GUI slot position and possible item stacks in a DEX recipe layout.
 *
 * @param x The relative X coordinate of the slot
 * @param y The relative Y coordinate of the slot
 * @param items The items that can occupy or cycle through this slot
 * @param isOutput True if this slot represents an output/result, false for input/catalyst
 */
public record DexRecipeSlot(int x, int y, List<ItemStack> items, boolean isOutput) {
    public DexRecipeSlot {
        items = items != null ? Collections.unmodifiableList(items) : Collections.emptyList();
    }
}
