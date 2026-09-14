package com.dex.recipe.brewing;

import net.minecraft.world.item.ItemStack;

public class BrewingRecipeEntry {
    private final ItemStack input;
    private final ItemStack ingredient;
    private final ItemStack output;

    public BrewingRecipeEntry(ItemStack input, ItemStack ingredient, ItemStack output) {
        this.input = input.copy();
        this.ingredient = ingredient.copy();
        this.output = output.copy();
    }

    public ItemStack getInput() {
        return input;
    }

    public ItemStack getIngredient() {
        return ingredient;
    }

    public ItemStack getOutput() {
        return output;
    }
}
