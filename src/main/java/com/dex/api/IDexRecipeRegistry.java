package com.dex.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Registry storing recipes grouped by category ID.
 */
public interface IDexRecipeRegistry {
    /**
     * Register a list of recipe objects under a category ID.
     */
    void registerRecipes(ResourceLocation categoryId, List<?> recipes);

    /**
     * Retrieve all recipes registered under a category ID.
     */
    <T> List<T> getRecipes(ResourceLocation categoryId);
}
