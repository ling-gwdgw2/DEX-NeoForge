package com.dex.plugin;

import com.dex.api.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Thread-safe central implementation for DEX Category, Workstation, and Recipe registries.
 * Automatically indexes custom machine recipes by Output and Input items for O(1) lookup.
 */
public class DexRegistriesImpl implements IDexCategoryRegistry, IDexWorkstationRegistry, IDexRecipeRegistry {

    public record CustomRecipeEntry(IDexRecipeCategory<?> category, Object recipe, List<DexRecipeSlot> slots) {}

    private final Map<ResourceLocation, IDexRecipeCategory<?>> categories = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<ItemStack>> workstationsByCategory = new HashMap<>();
    private final Map<Item, List<ResourceLocation>> categoriesByWorkstationItem = new HashMap<>();
    private final Map<ResourceLocation, List<Object>> recipesByCategory = new HashMap<>();

    private final Map<Item, List<CustomRecipeEntry>> customRecipesByOutput = new HashMap<>();
    private final Map<Item, List<CustomRecipeEntry>> customRecipesByInput = new HashMap<>();

    public synchronized void clear() {
        categories.clear();
        workstationsByCategory.clear();
        categoriesByWorkstationItem.clear();
        recipesByCategory.clear();
        customRecipesByOutput.clear();
        customRecipesByInput.clear();
    }

    // ==================== Category Registry ====================
    @Override
    public synchronized void registerCategory(IDexRecipeCategory<?> category) {
        if (category != null && category.getId() != null) {
            categories.put(category.getId(), category);
        }
    }

    @Override
    public synchronized Optional<IDexRecipeCategory<?>> getCategory(ResourceLocation id) {
        return Optional.ofNullable(categories.get(id));
    }

    @Override
    public synchronized Collection<IDexRecipeCategory<?>> getAllCategories() {
        return Collections.unmodifiableCollection(new ArrayList<>(categories.values()));
    }

    // ==================== Workstation Registry ====================
    @Override
    public synchronized void registerWorkstation(ResourceLocation categoryId, ItemStack workstation) {
        if (categoryId != null && workstation != null && !workstation.isEmpty()) {
            workstationsByCategory.computeIfAbsent(categoryId, k -> new ArrayList<>()).add(workstation.copy());
            categoriesByWorkstationItem.computeIfAbsent(workstation.getItem(), k -> new ArrayList<>()).add(categoryId);
        }
    }

    @Override
    public synchronized List<ItemStack> getWorkstations(ResourceLocation categoryId) {
        List<ItemStack> list = workstationsByCategory.get(categoryId);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    @Override
    public synchronized List<ResourceLocation> getCategoriesForWorkstation(ItemStack workstation) {
        if (workstation == null || workstation.isEmpty()) return Collections.emptyList();
        List<ResourceLocation> list = categoriesByWorkstationItem.get(workstation.getItem());
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    // ==================== Recipe Registry ====================
    @Override
    public synchronized void registerRecipes(ResourceLocation categoryId, List<?> recipes) {
        if (categoryId != null && recipes != null) {
            recipesByCategory.computeIfAbsent(categoryId, k -> new ArrayList<>()).addAll(recipes);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T> List<T> getRecipes(ResourceLocation categoryId) {
        List<Object> list = recipesByCategory.getOrDefault(categoryId, Collections.emptyList());
        return (List<T>) (List<?>) list;
    }

    // ==================== Custom Recipe Indexing ====================
    @SuppressWarnings("unchecked")
    public synchronized void indexCustomRecipes() {
        customRecipesByOutput.clear();
        customRecipesByInput.clear();

        for (Map.Entry<ResourceLocation, IDexRecipeCategory<?>> catEntry : categories.entrySet()) {
            IDexRecipeCategory<Object> category = (IDexRecipeCategory<Object>) catEntry.getValue();
            List<Object> recipeList = recipesByCategory.getOrDefault(catEntry.getKey(), Collections.emptyList());

            for (Object recipe : recipeList) {
                try {
                    List<DexRecipeSlot> slots = category.getSlots(recipe);
                    if (slots == null || slots.isEmpty()) continue;

                    CustomRecipeEntry entry = new CustomRecipeEntry(category, recipe, slots);

                    for (DexRecipeSlot slot : slots) {
                        for (ItemStack itemStack : slot.items()) {
                            if (itemStack != null && !itemStack.isEmpty()) {
                                Item item = itemStack.getItem();
                                if (slot.isOutput()) {
                                    customRecipesByOutput.computeIfAbsent(item, k -> new ArrayList<>()).add(entry);
                                } else {
                                    customRecipesByInput.computeIfAbsent(item, k -> new ArrayList<>()).add(entry);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public synchronized List<CustomRecipeEntry> getCustomRecipesForOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        List<CustomRecipeEntry> list = customRecipesByOutput.get(stack.getItem());
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    public synchronized List<CustomRecipeEntry> getCustomRecipesForInput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        List<CustomRecipeEntry> list = customRecipesByInput.get(stack.getItem());
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    public synchronized boolean hasCustomRecipes(ItemStack stack) {
        return !getCustomRecipesForOutput(stack).isEmpty();
    }

    public synchronized boolean hasCustomUsages(ItemStack stack) {
        return !getCustomRecipesForInput(stack).isEmpty();
    }
}
