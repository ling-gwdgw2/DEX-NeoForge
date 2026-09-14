package com.dex.plugin;

import com.dex.DEXMod;
import com.dex.api.DexPlugin;
import com.dex.api.IDexPlugin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Discovers and manages all DEX plugins (both internal and external mod plugins).
 */
public class DexPluginManager {
    private static final DexPluginManager INSTANCE = new DexPluginManager();

    private final List<IDexPlugin> plugins = new ArrayList<>();
    private final DexRegistriesImpl registries = new DexRegistriesImpl();
    private boolean discovered = false;
    private boolean initialized = false;

    public static DexPluginManager getInstance() {
        return INSTANCE;
    }

    public DexRegistriesImpl getRegistries() {
        return registries;
    }

    public List<IDexPlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    /**
     * Manually registers a plugin instance.
     */
    public synchronized void registerPlugin(IDexPlugin plugin) {
        if (plugin != null && !plugins.contains(plugin)) {
            plugins.add(plugin);
            DEXMod.LOGGER.info("Registered DEX Plugin: {}", plugin.getClass().getName());
        }
    }

    /**
     * Scans for classes annotated with @DexPlugin across all loaded mods.
     */
    public synchronized void discoverPlugins() {
        if (discovered) return;
        discovered = true;

        DEXMod.LOGGER.info("Scanning for DEX plugins across loaded mods...");
        String targetAnnotationDesc = Type.getDescriptor(DexPlugin.class);
        String targetAnnotationClass = DexPlugin.class.getName();

        try {
            for (ModFileScanData scanData : ModList.get().getAllScanData()) {
                for (ModFileScanData.AnnotationData annotation : scanData.getAnnotations()) {
                    Type annotationType = annotation.annotationType();
                    if (targetAnnotationDesc.equals(annotationType.getDescriptor())
                            || targetAnnotationClass.equals(annotationType.getClassName())) {
                        String className = annotation.memberName();
                        try {
                            Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                            if (IDexPlugin.class.isAssignableFrom(clazz)) {
                                IDexPlugin plugin = (IDexPlugin) clazz.getDeclaredConstructor().newInstance();
                                registerPlugin(plugin);
                            } else {
                                DEXMod.LOGGER.warn("Class {} is annotated with @DexPlugin but does not implement IDexPlugin", className);
                            }
                        } catch (Throwable t) {
                            DEXMod.LOGGER.error("Failed to instantiate DEX plugin {}: {}", className, t.getMessage(), t);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            DEXMod.LOGGER.error("Error during DEX plugin discovery: {}", t.getMessage(), t);
        }

        DEXMod.LOGGER.info("DEX Plugin discovery complete. Found {} plugin(s).", plugins.size());
    }

    /**
     * Re-initializes all plugins with the current game level and recipe data.
     */
    public synchronized void initializeAll(ClientLevel level) {
        discoverPlugins();

        DEXMod.LOGGER.info("Initializing DEX Plugins for level: {}", level != null ? level.dimension().location() : "null");
        registries.clear();

        // 1. Categories
        for (IDexPlugin plugin : plugins) {
            try {
                plugin.registerCategories(registries);
            } catch (Throwable t) {
                DEXMod.LOGGER.error("Plugin {} threw exception during registerCategories: {}",
                        plugin.getClass().getName(), t.getMessage(), t);
            }
        }

        // 2. Workstations
        for (IDexPlugin plugin : plugins) {
            try {
                plugin.registerWorkstations(registries);
            } catch (Throwable t) {
                DEXMod.LOGGER.error("Plugin {} threw exception during registerWorkstations: {}",
                        plugin.getClass().getName(), t.getMessage(), t);
            }
        }

        // 3. Recipes
        for (IDexPlugin plugin : plugins) {
            try {
                plugin.registerRecipes(registries, level);
            } catch (Throwable t) {
                DEXMod.LOGGER.error("Plugin {} threw exception during registerRecipes: {}",
                        plugin.getClass().getName(), t.getMessage(), t);
            }
        }

        // 4. JEI Compatibility Bridge
        try {
            com.dex.compat.jei.JeiBridgeManager.getInstance().initializeAll(registries, level);
        } catch (Throwable t) {
            DEXMod.LOGGER.error("Failed to run JEI compatibility bridge: {}", t.getMessage(), t);
        }

        // 5. Build Reverse Item-to-Recipe Index for all custom categories
        registries.indexCustomRecipes();

        initialized = true;
        DEXMod.LOGGER.info("DEX Plugins initialized: {} categories registered.", registries.getAllCategories().size());
    }

    public boolean isInitialized() {
        return initialized;
    }
}
