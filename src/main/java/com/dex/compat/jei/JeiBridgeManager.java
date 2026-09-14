package com.dex.compat.jei;

import com.dex.DEXMod;
import com.dex.plugin.DexRegistriesImpl;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Manages discovery, lifecycle, and integration of third-party JEI plugins.
 * Acts as a host providing mock registration interfaces to capture categories and recipes.
 */
public class JeiBridgeManager {
    private static final JeiBridgeManager INSTANCE = new JeiBridgeManager();

    private final List<IModPlugin> jeiPlugins = new ArrayList<>();
    private boolean discovered = false;

    public static JeiBridgeManager getInstance() {
        return INSTANCE;
    }

    public List<IModPlugin> getJeiPlugins() {
        return Collections.unmodifiableList(jeiPlugins);
    }

    /**
     * Scans for classes annotated with @JeiPlugin across all loaded mods in the Minecraft instance.
     */
    public synchronized void discoverPlugins() {
        if (discovered) return;
        discovered = true;

        DEXMod.LOGGER.info("Scanning for JEI plugins (@mezz.jei.api.JeiPlugin) across loaded mods...");
        String jeiAnnotationDesc = "Lmezz/jei/api/JeiPlugin;";
        String jeiAnnotationName = "mezz.jei.api.JeiPlugin";

        try {
            for (ModFileScanData scanData : ModList.get().getAllScanData()) {
                for (ModFileScanData.AnnotationData annotation : scanData.getAnnotations()) {
                    Type annotationType = annotation.annotationType();
                    if (jeiAnnotationDesc.equals(annotationType.getDescriptor())
                            || jeiAnnotationName.equals(annotationType.getClassName())) {
                        String className = annotation.memberName();
                        try {
                            Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                            if (IModPlugin.class.isAssignableFrom(clazz)) {
                                IModPlugin plugin = (IModPlugin) clazz.getDeclaredConstructor().newInstance();
                                if (!jeiPlugins.contains(plugin)) {
                                    jeiPlugins.add(plugin);
                                    DEXMod.LOGGER.info("Discovered and loaded JEI Plugin: {} ({})",
                                            className, plugin.getPluginUid());
                                }
                            } else {
                                DEXMod.LOGGER.warn("Class {} is annotated with @JeiPlugin but does not implement IModPlugin", className);
                            }
                        } catch (Throwable t) {
                            DEXMod.LOGGER.debug("Skipped JEI plugin candidate {}: {}", className, t.getMessage());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            DEXMod.LOGGER.error("Error during JEI plugin discovery: {}", t.getMessage(), t);
        }

        DEXMod.LOGGER.info("JEI Plugin discovery complete. Found {} plugin(s).", jeiPlugins.size());
    }

    /**
     * Runs JEI registration cycles and adapts captured data into DEX's registries.
     */
    @SuppressWarnings("unchecked")
    public synchronized void initializeAll(DexRegistriesImpl dexRegistries, ClientLevel level) {
        discoverPlugins();
        if (jeiPlugins.isEmpty()) return;

        DEXMod.LOGGER.info("Invoking JEI Plugin registrations for {} plugin(s)...", jeiPlugins.size());

        MockJeiRegistrations.CategoryRegistration categoryRegistration = new MockJeiRegistrations.CategoryRegistration();
        MockJeiRegistrations.CatalystRegistration catalystRegistration = new MockJeiRegistrations.CatalystRegistration();
        MockJeiRegistrations.RecipeRegistration recipeRegistration = new MockJeiRegistrations.RecipeRegistration();

        // 1. Categories
        for (IModPlugin plugin : jeiPlugins) {
            try {
                plugin.registerCategories(categoryRegistration);
            } catch (Throwable t) {
                DEXMod.LOGGER.warn("JEI Plugin {} error in registerCategories: {}", plugin.getPluginUid(), t.getMessage());
            }
        }

        // 2. Catalysts (Workstations)
        for (IModPlugin plugin : jeiPlugins) {
            try {
                plugin.registerRecipeCatalysts(catalystRegistration);
            } catch (Throwable t) {
                DEXMod.LOGGER.warn("JEI Plugin {} error in registerRecipeCatalysts: {}", plugin.getPluginUid(), t.getMessage());
            }
        }

        // 3. Recipes
        for (IModPlugin plugin : jeiPlugins) {
            try {
                plugin.registerRecipes(recipeRegistration);
            } catch (Throwable t) {
                DEXMod.LOGGER.warn("JEI Plugin {} error in registerRecipes: {}", plugin.getPluginUid(), t.getMessage());
            }
        }

        // 4. Adapt into DEX Registries
        Map<RecipeType<?>, List<ItemStack>> catalystMap = catalystRegistration.getCatalysts();
        int categoryCount = 0;
        int recipeCount = 0;

        for (IRecipeCategory<?> jeiCategory : categoryRegistration.getCategories()) {
            try {
                RecipeType<?> recipeType = jeiCategory.getRecipeType();
                ResourceLocation categoryId = recipeType != null ? recipeType.getUid() : null;
                if (categoryId == null) continue;

                // Pick primary workstation icon if available
                List<ItemStack> workstations = catalystMap.getOrDefault(recipeType, Collections.emptyList());
                ItemStack icon = (!workstations.isEmpty()) ? workstations.get(0) : ItemStack.EMPTY;

                // Register category
                JeiCategoryAdapter<Object> adapter = new JeiCategoryAdapter<>((IRecipeCategory<Object>) jeiCategory, icon);
                dexRegistries.registerCategory(adapter);
                categoryCount++;

                // Register workstations
                for (ItemStack workstation : workstations) {
                    dexRegistries.registerWorkstation(categoryId, workstation);
                }

                // Register recipes
                List<Object> recipes = recipeRegistration.getRecipes().get(recipeType);
                if (recipes != null && !recipes.isEmpty()) {
                    dexRegistries.registerRecipes(categoryId, recipes);
                    recipeCount += recipes.size();
                }
            } catch (Throwable t) {
                DEXMod.LOGGER.debug("Failed to adapt JEI category: {}", t.getMessage());
            }
        }

        DEXMod.LOGGER.info("DEX - JEI Compatibility Bridge initialized: {} categories and {} recipes imported.",
                categoryCount, recipeCount);
    }
}
