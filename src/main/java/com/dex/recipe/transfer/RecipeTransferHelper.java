package com.dex.recipe.transfer;

import com.dex.recipe.RecipeIngredientHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.*;

/**
 * Validates player inventory ingredients for a recipe and handles auto-transfer.
 */
public final class RecipeTransferHelper {

    private RecipeTransferHelper() {}

    public record InventoryCheckResult(
            boolean allPresent,
            Set<Integer> missingSlotIndices,
            Map<Item, Integer> missingQuantities
    ) {}

    /**
     * Inspects the player's inventory against the ingredients required by the recipe.
     */
    public static InventoryCheckResult checkInventory(Recipe<?> recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || recipe == null) {
            return new InventoryCheckResult(true, Collections.emptySet(), Collections.emptyMap());
        }

        List<Ingredient> ingredients = RecipeIngredientHelper.getRawIngredients(recipe);
        if (ingredients.isEmpty()) {
            return new InventoryCheckResult(true, Collections.emptySet(), Collections.emptyMap());
        }

        // 1. Build available items count from player inventory
        Inventory inv = mc.player.getInventory();
        Map<Item, Integer> available = new HashMap<>();
        for (ItemStack stack : inv.items) {
            if (stack != null && !stack.isEmpty()) {
                available.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        Set<Integer> missingIndices = new HashSet<>();
        Map<Item, Integer> missingQuantities = new LinkedHashMap<>();

        // 2. Check each slot ingredient
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ing = ingredients.get(i);
            if (ing == null || ing.isEmpty()) continue;

            ItemStack[] matchItems = ing.getItems();
            boolean found = false;

            for (ItemStack candidate : matchItems) {
                if (candidate == null || candidate.isEmpty()) continue;
                Item item = candidate.getItem();
                int currentCount = available.getOrDefault(item, 0);
                if (currentCount > 0) {
                    available.put(item, currentCount - 1);
                    found = true;
                    break;
                }
            }

            if (!found) {
                missingIndices.add(i);
                if (matchItems.length > 0 && matchItems[0] != null && !matchItems[0].isEmpty()) {
                    missingQuantities.merge(matchItems[0].getItem(), 1, Integer::sum);
                }
            }
        }

        boolean allPresent = missingIndices.isEmpty();
        return new InventoryCheckResult(allPresent, missingIndices, missingQuantities);
    }

    /**
     * Executes auto-transfer packet to Crafting Table container.
     */
    public static void executeTransfer(RecipeHolder<?> holder, Screen parentScreen) {
        Minecraft mc = Minecraft.getInstance();
        if (holder == null || mc.player == null || mc.getConnection() == null) return;

        if (mc.player.containerMenu != null) {
            int containerId = mc.player.containerMenu.containerId;
            boolean shift = Screen.hasShiftDown();

            mc.getConnection().send(new ServerboundPlaceRecipePacket(containerId, holder, shift));
            mc.player.displayClientMessage(
                    Component.literal("DEX: Transferred recipe ingredients into Crafting Table!")
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
            mc.setScreen(parentScreen);
        } else {
            mc.player.displayClientMessage(
                    Component.literal("DEX: Open a Crafting Table to auto-fill!")
                            .withStyle(ChatFormatting.RED),
                    true
            );
        }
    }
}
