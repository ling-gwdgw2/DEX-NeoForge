package com.dex.api;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Defines a distinct recipe category or custom workstation screen in DEX.
 *
 * @param <T> The recipe object type (e.g. RecipeHolder, CustomMachineRecipe)
 */
public interface IDexRecipeCategory<T> {
    /**
     * Unique identifier for this category (e.g. "minecraft:crafting", "create:mechanical_crafting")
     */
    ResourceLocation getId();

    /**
     * Display title for the category tab/header.
     */
    Component getTitle();

    /**
     * Icon representing this category (e.g. Crafting Table, Furnace, Crusher).
     */
    ItemStack getIcon();

    /**
     * Width of the recipe layout area in pixels (default: 160).
     */
    default int getDisplayWidth() {
        return 160;
    }

    /**
     * Height of the recipe layout area in pixels (default: 120).
     */
    default int getDisplayHeight() {
        return 120;
    }

    /**
     * Optional custom rendering hook (e.g. background frames, arrows, progress bars, flame animations).
     */
    default void draw(T recipe, GuiGraphics graphics, double mouseX, double mouseY) {
    }

    /**
     * Returns the layout slot positions and ingredients for this recipe.
     */
    default java.util.List<DexRecipeSlot> getSlots(T recipe) {
        return java.util.Collections.emptyList();
    }
}
