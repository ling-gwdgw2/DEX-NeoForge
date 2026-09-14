package com.dex.recipe.tree;

import com.dex.recipe.RecipeIndexManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.*;

public class CraftingTreeCalculator {
    private static final int MAX_DEPTH = 6;

    public static CraftingTreeNode calculateTree(ItemStack targetItem, int count) {
        if (targetItem == null || targetItem.isEmpty() || count <= 0) {
            return new CraftingTreeNode(ItemStack.EMPTY, 0, true);
        }

        Set<Item> visited = new HashSet<>();
        return decompose(targetItem, count, visited, 0);
    }

    private static CraftingTreeNode decompose(ItemStack stack, int count, Set<Item> visited, int depth) {
        Item item = stack.getItem();

        // Prevent recursive loop (e.g., Iron Ingot <-> Iron Block) or deep recursion
        if (depth >= MAX_DEPTH || visited.contains(item)) {
            return new CraftingTreeNode(stack, count, true);
        }

        List<RecipeHolder<?>> recipes = RecipeIndexManager.getInstance().getRecipesFor(stack);
        RecipeHolder<?> primaryRecipeHolder = null;

        // Prefer CraftingRecipe
        for (RecipeHolder<?> holder : recipes) {
            if (holder.value() instanceof CraftingRecipe) {
                primaryRecipeHolder = holder;
                break;
            }
        }
        // Fallback to any recipe
        if (primaryRecipeHolder == null && !recipes.isEmpty()) {
            primaryRecipeHolder = recipes.get(0);
        }

        if (primaryRecipeHolder == null) {
            // No recipe exists -> Base Material
            return new CraftingTreeNode(stack, count, true);
        }

        Recipe<?> recipe = primaryRecipeHolder.value();
        HolderLookup.Provider registryAccess = Minecraft.getInstance().level != null 
                ? Minecraft.getInstance().level.registryAccess() 
                : null;

        ItemStack resultStack = recipe.getResultItem(registryAccess);
        int resultCount = Math.max(1, resultStack.getCount());

        int craftsNeeded = (int) Math.ceil((double) count / resultCount);

        CraftingTreeNode node = new CraftingTreeNode(stack, count, false);
        visited.add(item);

        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        // Consolidate ingredient counts for this craft
        Map<Item, Integer> ingredientQuantities = new LinkedHashMap<>();
        Map<Item, ItemStack> sampleStacks = new HashMap<>();

        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) continue;
            ItemStack[] matching = ingredient.getItems();
            if (matching.length == 0) continue;

            ItemStack candidate = matching[0];
            Item candidateItem = candidate.getItem();

            ingredientQuantities.merge(candidateItem, 1, Integer::sum);
            sampleStacks.putIfAbsent(candidateItem, candidate);
        }

        for (Map.Entry<Item, Integer> entry : ingredientQuantities.entrySet()) {
            Item ingItem = entry.getKey();
            int qtyPerRecipe = entry.getValue();
            int totalIngNeeded = qtyPerRecipe * craftsNeeded;

            ItemStack sample = sampleStacks.get(ingItem);
            CraftingTreeNode childNode = decompose(sample, totalIngNeeded, new HashSet<>(visited), depth + 1);
            node.addChild(childNode);
        }

        visited.remove(item);
        return node;
    }
}
