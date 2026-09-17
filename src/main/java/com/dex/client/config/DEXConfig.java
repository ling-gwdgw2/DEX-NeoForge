package com.dex.client.config;

import com.dex.DEXMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Client configuration manager for DEX settings.
 * Persists user preferences into config/dex_client.json.
 */
public class DEXConfig {
    private static final DEXConfig INSTANCE = new DEXConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/dex_client.json");

    private boolean cheatMode = false;
    private boolean showLeftBookmarkPanel = true;
    private boolean showModTooltips = true;
    private boolean highlightMissingIngredients = true;

    private boolean loaded = false;

    public static DEXConfig getInstance() {
        return INSTANCE;
    }

    public static DEXConfig get() {
        return INSTANCE;
    }

    public synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load();
    }

    public boolean isCheatMode() {
        ensureLoaded();
        return cheatMode;
    }

    public void setCheatMode(boolean cheatMode) {
        this.cheatMode = cheatMode;
        save();
    }

    public boolean toggleCheatMode() {
        ensureLoaded();
        this.cheatMode = !this.cheatMode;
        save();
        return this.cheatMode;
    }

    public boolean isShowLeftBookmarkPanel() {
        ensureLoaded();
        return showLeftBookmarkPanel;
    }

    public void setShowLeftBookmarkPanel(boolean showLeftBookmarkPanel) {
        this.showLeftBookmarkPanel = showLeftBookmarkPanel;
        save();
    }

    public boolean isShowModTooltips() {
        ensureLoaded();
        return showModTooltips;
    }

    public void setShowModTooltips(boolean showModTooltips) {
        this.showModTooltips = showModTooltips;
        save();
    }

    public boolean isHighlightMissingIngredients() {
        ensureLoaded();
        return highlightMissingIngredients;
    }

    public void setHighlightMissingIngredients(boolean highlightMissingIngredients) {
        this.highlightMissingIngredients = highlightMissingIngredients;
        save();
    }

    public synchronized void load() {
        try {
            if (CONFIG_FILE.exists()) {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    ConfigData data = GSON.fromJson(reader, ConfigData.class);
                    if (data != null) {
                        this.cheatMode = data.cheatMode;
                        this.showLeftBookmarkPanel = data.showLeftBookmarkPanel;
                        this.showModTooltips = data.showModTooltips;
                        this.highlightMissingIngredients = data.highlightMissingIngredients;
                    }
                }
            }
        } catch (Exception e) {
            DEXMod.LOGGER.error("Failed to load DEX client config: {}", e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                ConfigData data = new ConfigData(
                        this.cheatMode,
                        this.showLeftBookmarkPanel,
                        this.showModTooltips,
                        this.highlightMissingIngredients
                );
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            DEXMod.LOGGER.error("Failed to save DEX client config: {}", e.getMessage());
        }
    }

    private static class ConfigData {
        boolean cheatMode;
        boolean showLeftBookmarkPanel;
        boolean showModTooltips;
        boolean highlightMissingIngredients;

        ConfigData(boolean cheatMode, boolean showLeftBookmarkPanel, boolean showModTooltips, boolean highlightMissingIngredients) {
            this.cheatMode = cheatMode;
            this.showLeftBookmarkPanel = showLeftBookmarkPanel;
            this.showModTooltips = showModTooltips;
            this.highlightMissingIngredients = highlightMissingIngredients;
        }
    }
}
