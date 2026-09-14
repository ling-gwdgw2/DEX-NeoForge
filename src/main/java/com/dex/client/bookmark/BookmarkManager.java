package com.dex.client.bookmark;

import com.dex.DEXMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Manages pinned/bookmarked items for quick access (toggled via 'A' key).
 * Automatically persists bookmarks to config/dex_bookmarks.json.
 */
public class BookmarkManager {
    private static final BookmarkManager INSTANCE = new BookmarkManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/dex_bookmarks.json");

    private final List<ItemStack> bookmarkedStacks = new ArrayList<>();
    private final Set<Item> bookmarkedItems = new HashSet<>();
    private boolean loaded = false;

    public static BookmarkManager getInstance() {
        return INSTANCE;
    }

    public synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load();
    }

    public synchronized boolean toggleBookmark(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ensureLoaded();

        Item item = stack.getItem();
        if (bookmarkedItems.contains(item)) {
            bookmarkedItems.remove(item);
            bookmarkedStacks.removeIf(s -> s.getItem() == item);
            save();
            return false; // Removed
        } else {
            bookmarkedItems.add(item);
            bookmarkedStacks.add(stack.copyWithCount(1));
            save();
            return true; // Added
        }
    }

    public synchronized boolean isBookmarked(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ensureLoaded();
        return bookmarkedItems.contains(stack.getItem());
    }

    public synchronized List<ItemStack> getBookmarks() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(bookmarkedStacks));
    }

    public synchronized void load() {
        bookmarkedStacks.clear();
        bookmarkedItems.clear();

        if (!CONFIG_FILE.exists()) return;

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> ids = GSON.fromJson(reader, listType);
            if (ids != null) {
                for (String id : ids) {
                    ResourceLocation loc = ResourceLocation.tryParse(id);
                    if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) {
                        Item item = BuiltInRegistries.ITEM.get(loc);
                        if (item != Items.AIR && !bookmarkedItems.contains(item)) {
                            bookmarkedItems.add(item);
                            bookmarkedStacks.add(new ItemStack(item));
                        }
                    }
                }
            }
            DEXMod.LOGGER.info("Loaded {} bookmarked items from config.", bookmarkedStacks.size());
        } catch (Throwable t) {
            DEXMod.LOGGER.error("Failed to load DEX bookmarks: {}", t.getMessage());
        }
    }

    public synchronized void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            List<String> ids = new ArrayList<>();
            for (ItemStack stack : bookmarkedStacks) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                ids.add(key.toString());
            }

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(ids, writer);
            }
        } catch (Throwable t) {
            DEXMod.LOGGER.error("Failed to save DEX bookmarks: {}", t.getMessage());
        }
    }
}
