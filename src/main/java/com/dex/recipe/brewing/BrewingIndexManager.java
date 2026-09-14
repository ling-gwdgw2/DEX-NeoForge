package com.dex.recipe.brewing;

import com.dex.DEXMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;

import java.util.*;

public class BrewingIndexManager {
    private static final BrewingIndexManager INSTANCE = new BrewingIndexManager();

    private final List<BrewingRecipeEntry> allBrewingRecipes = new ArrayList<>();
    private final Map<Item, List<BrewingRecipeEntry>> recipesByOutput = new HashMap<>();
    private final Map<Item, List<BrewingRecipeEntry>> recipesByIngredient = new HashMap<>();

    public static BrewingIndexManager getInstance() {
        return INSTANCE;
    }

    public synchronized void reindex(ClientLevel level) {
        if (level == null) return;
        PotionBrewing potionBrewing = level.potionBrewing();
        if (potionBrewing == null) return;

        allBrewingRecipes.clear();
        recipesByOutput.clear();
        recipesByIngredient.clear();

        // 1. Gather all potion items
        List<ItemStack> testInputs = new ArrayList<>();
        List<Item> potionContainers = List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);

        for (Holder<Potion> potionHolder : BuiltInRegistries.POTION.asHolderIdMap()) {
            for (Item container : potionContainers) {
                testInputs.add(PotionContents.createItemStack(container, potionHolder));
            }
        }

        // 2. Candidate brewing ingredients
        List<ItemStack> candidateIngredients = List.of(
                new ItemStack(Items.NETHER_WART),
                new ItemStack(Items.REDSTONE),
                new ItemStack(Items.GLOWSTONE_DUST),
                new ItemStack(Items.GUNPOWDER),
                new ItemStack(Items.DRAGON_BREATH),
                new ItemStack(Items.FERMENTED_SPIDER_EYE),
                new ItemStack(Items.SUGAR),
                new ItemStack(Items.RABBIT_FOOT),
                new ItemStack(Items.GLISTERING_MELON_SLICE),
                new ItemStack(Items.SPIDER_EYE),
                new ItemStack(Items.PUFFERFISH),
                new ItemStack(Items.MAGMA_CREAM),
                new ItemStack(Items.GOLDEN_CARROT),
                new ItemStack(Items.BLAZE_POWDER),
                new ItemStack(Items.GHAST_TEAR),
                new ItemStack(Items.TURTLE_HELMET),
                new ItemStack(Items.PHANTOM_MEMBRANE),
                new ItemStack(Items.BREEZE_ROD),
                new ItemStack(Items.COBWEB),
                new ItemStack(Items.SLIME_BLOCK),
                new ItemStack(Items.STONE)
        );

        // 3. Test mixes via vanilla PotionBrewing
        for (ItemStack input : testInputs) {
            for (ItemStack ingredient : candidateIngredients) {
                try {
                    if (potionBrewing.hasMix(input, ingredient)) {
                        ItemStack output = potionBrewing.mix(ingredient, input);
                        if (!output.isEmpty() && !ItemStack.matches(input, output)) {
                            addRecipe(new BrewingRecipeEntry(input, ingredient, output));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 4. Modded NeoForge IBrewingRecipe
        try {
            for (IBrewingRecipe recipe : potionBrewing.getRecipes()) {
                // Modded brewing recipes
                for (ItemStack input : testInputs) {
                    for (ItemStack ing : candidateIngredients) {
                        if (recipe.isInput(input) && recipe.isIngredient(ing)) {
                            ItemStack out = recipe.getOutput(input, ing);
                            if (!out.isEmpty()) {
                                addRecipe(new BrewingRecipeEntry(input, ing, out));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        DEXMod.LOGGER.info("DEX Brewing Indexing completed: {} brewing mixes indexed.", allBrewingRecipes.size());
    }

    private void addRecipe(BrewingRecipeEntry entry) {
        // Prevent exact duplicates
        for (BrewingRecipeEntry existing : allBrewingRecipes) {
            if (ItemStack.matches(existing.getInput(), entry.getInput())
                    && ItemStack.matches(existing.getIngredient(), entry.getIngredient())
                    && ItemStack.matches(existing.getOutput(), entry.getOutput())) {
                return;
            }
        }
        allBrewingRecipes.add(entry);
        recipesByOutput.computeIfAbsent(entry.getOutput().getItem(), k -> new ArrayList<>()).add(entry);
        recipesByIngredient.computeIfAbsent(entry.getIngredient().getItem(), k -> new ArrayList<>()).add(entry);
    }

    public List<BrewingRecipeEntry> getRecipesFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        List<BrewingRecipeEntry> results = new ArrayList<>();
        for (BrewingRecipeEntry entry : allBrewingRecipes) {
            if (entry.getOutput().getItem() == stack.getItem()) {
                // If potions, check PotionContents
                PotionContents pOut = entry.getOutput().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                PotionContents pTarget = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                if (pOut != null && pTarget != null) {
                    if (Objects.equals(pOut.potion(), pTarget.potion())) {
                        results.add(entry);
                    }
                } else {
                    results.add(entry);
                }
            }
        }
        return results;
    }

    public List<BrewingRecipeEntry> getUsagesFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        List<BrewingRecipeEntry> results = new ArrayList<>();
        for (BrewingRecipeEntry entry : allBrewingRecipes) {
            if (entry.getIngredient().getItem() == stack.getItem() || entry.getInput().getItem() == stack.getItem()) {
                results.add(entry);
            }
        }
        return results;
    }

    public boolean hasBrewing(ItemStack stack) {
        return !getRecipesFor(stack).isEmpty() || !getUsagesFor(stack).isEmpty();
    }
}
