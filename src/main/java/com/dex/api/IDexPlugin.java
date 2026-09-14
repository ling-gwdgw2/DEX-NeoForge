package com.dex.api;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Main plugin entry point for DEX integration.
 * Classes implementing this interface should be annotated with {@link DexPlugin}.
 */
public interface IDexPlugin {
    /**
     * Register custom recipe categories (workstations, custom machine recipe types).
     */
    default void registerCategories(IDexCategoryRegistry registry) {
    }

    /**
     * Register workstations (blocks/items) used to execute recipes in categories.
     */
    default void registerWorkstations(IDexWorkstationRegistry registry) {
    }

    /**
     * Register recipe entries into categories.
     */
    default void registerRecipes(IDexRecipeRegistry registry, ClientLevel level) {
    }
}
