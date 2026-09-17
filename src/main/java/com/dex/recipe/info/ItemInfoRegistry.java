package com.dex.recipe.info;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores lore, descriptions, and informational guides for items (captured from JEI plugins or mods).
 */
public class ItemInfoRegistry {
    private static final ItemInfoRegistry INSTANCE = new ItemInfoRegistry();

    private final Map<Item, List<Component>> infoMap = new ConcurrentHashMap<>();

    public static ItemInfoRegistry getInstance() {
        return INSTANCE;
    }

    public void addInfo(Item item, Component... components) {
        if (item == null || components == null || components.length == 0) return;
        List<Component> list = infoMap.computeIfAbsent(item, k -> new ArrayList<>());
        Collections.addAll(list, components);
    }

    public void addInfo(Item item, List<Component> components) {
        if (item == null || components == null || components.isEmpty()) return;
        infoMap.computeIfAbsent(item, k -> new ArrayList<>()).addAll(components);
    }

    public void addInfo(ItemStack stack, Component... components) {
        if (stack == null || stack.isEmpty()) return;
        addInfo(stack.getItem(), components);
    }

    public List<Component> getInfo(Item item) {
        if (item == null) return Collections.emptyList();
        return infoMap.getOrDefault(item, Collections.emptyList());
    }

    public boolean hasInfo(Item item) {
        if (item == null) return false;
        List<Component> list = infoMap.get(item);
        return list != null && !list.isEmpty();
    }

    public void clear() {
        infoMap.clear();
    }
}
