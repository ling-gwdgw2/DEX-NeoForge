package com.dex.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry for custom recipe categories and workstations.
 */
public interface IDexCategoryRegistry {
    /**
     * Register a new recipe category.
     */
    void registerCategory(IDexRecipeCategory<?> category);

    /**
     * Look up a category by its unique ID.
     */
    Optional<IDexRecipeCategory<?>> getCategory(ResourceLocation id);

    /**
     * Retrieve all currently registered categories.
     */
    Collection<IDexRecipeCategory<?>> getAllCategories();
}
