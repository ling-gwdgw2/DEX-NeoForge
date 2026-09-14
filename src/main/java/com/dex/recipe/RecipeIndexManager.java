package com.dex.recipe;

import com.dex.DEXMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.util.*;

public class RecipeIndexManager {
    private static final RecipeIndexManager INSTANCE = new RecipeIndexManager();

    private final Map<Item, List<RecipeHolder<?>>> recipesByOutput = new HashMap<>();
    private final Map<Item, List<RecipeHolder<?>>> recipesByInput = new HashMap<>();
    private boolean indexed = false;

    public static RecipeIndexManager getInstance() {
        return INSTANCE;
    }

    public synchronized void reindex(ClientLevel level) {
        if (level == null) return;
        RecipeManager recipeManager = level.getRecipeManager();
        if (recipeManager == null) return;

        DEXMod.LOGGER.info("Starting DEX Recipe Indexing...");
        recipesByOutput.clear();
        recipesByInput.clear();

        HolderLookup.Provider registryAccess = level.registryAccess();
        Collection<RecipeHolder<?>> allRecipes = recipeManager.getRecipes();

        int total = 0;
        int errorCount = 0;

        for (RecipeHolder<?> holder : allRecipes) {
            total++;
            try {
                Recipe<?> recipe = holder.value();
                if (recipe == null) continue;

                // 1. Output Item
                ItemStack outputStack = recipe.getResultItem(registryAccess);
                if (outputStack != null && !outputStack.isEmpty()) {
                    recipesByOutput.computeIfAbsent(outputStack.getItem(), k -> new ArrayList<>()).add(holder);
                }

                // 2. Ingredients / Inputs
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient == null || ingredient.isEmpty()) continue;
                    for (ItemStack inputStack : ingredient.getItems()) {
                        if (inputStack != null && !inputStack.isEmpty()) {
                            List<RecipeHolder<?>> list = recipesByInput.computeIfAbsent(inputStack.getItem(), k -> new ArrayList<>());
                            if (!list.contains(holder)) {
                                list.add(holder);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                errorCount++;
                DEXMod.LOGGER.debug("Failed to index recipe {}: {}", holder.id(), e.getMessage());
            }
        }

        indexed = true;
        DEXMod.LOGGER.info("DEX Recipe Indexing completed: {} recipes indexed ({} skipped with errors). Outputs mapped: {}, Inputs mapped: {}",
                total, errorCount, recipesByOutput.size(), recipesByInput.size());
    }

    public List<RecipeHolder<?>> getRecipesFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        return recipesByOutput.getOrDefault(stack.getItem(), Collections.emptyList());
    }

    public List<RecipeHolder<?>> getUsagesFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        return recipesByInput.getOrDefault(stack.getItem(), Collections.emptyList());
    }

    public boolean hasRecipes(ItemStack stack) {
        return !getRecipesFor(stack).isEmpty();
    }

    public boolean hasUsages(ItemStack stack) {
        return !getUsagesFor(stack).isEmpty();
    }

    public boolean isIndexed() {
        return indexed;
    }
}
