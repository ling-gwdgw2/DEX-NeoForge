package com.dex.plugin;

import com.dex.api.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe central implementation for DEX Category, Workstation, and Recipe registries.
 */
public class DexRegistriesImpl implements IDexCategoryRegistry, IDexWorkstationRegistry, IDexRecipeRegistry {
    private final Map<ResourceLocation, IDexRecipeCategory<?>> categories = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<ItemStack>> workstationsByCategory = new HashMap<>();
    private final Map<Item, List<ResourceLocation>> categoriesByWorkstationItem = new HashMap<>();
    private final Map<ResourceLocation, List<Object>> recipesByCategory = new HashMap<>();

    public synchronized void clear() {
        categories.clear();
        workstationsByCategory.clear();
        categoriesByWorkstationItem.clear();
        recipesByCategory.clear();
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
}
