package com.dex.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Registry mapping crafting stations/blocks to category IDs.
 * Allows DEX to show which block or machine is used to make a recipe.
 */
public interface IDexWorkstationRegistry {
    /**
     * Register a workstation item (e.g. Crafting Table, Furnace, Mechanical Press) for a category.
     */
    void registerWorkstation(ResourceLocation categoryId, ItemStack workstation);

    /**
     * Get all workstation items associated with a category.
     */
    List<ItemStack> getWorkstations(ResourceLocation categoryId);

    /**
     * Get all category IDs associated with a specific workstation item.
     */
    List<ResourceLocation> getCategoriesForWorkstation(ItemStack workstation);
}
