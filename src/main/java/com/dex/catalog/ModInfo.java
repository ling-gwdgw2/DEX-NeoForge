package com.dex.catalog;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModInfo {
    private final String modId;
    private final String displayName;
    private final String description;
    private final String version;
    private final List<ItemStack> items = new ArrayList<>();
    private ItemStack representativeItem = ItemStack.EMPTY;

    public ModInfo(String modId, String displayName, String description, String version) {
        this.modId = modId;
        this.displayName = displayName != null && !displayName.isBlank() ? displayName : modId;
        this.description = description != null ? description : "";
        this.version = version != null ? version : "";
    }

    public void addItem(ItemStack stack) {
        if (!stack.isEmpty()) {
            this.items.add(stack);
            if (this.representativeItem.isEmpty()) {
                // If it's a valid non-empty item, use as candidate representative item
                if (stack.getItem() != Items.AIR) {
                    this.representativeItem = stack.copy();
                }
            }
        }
    }

    public void setRepresentativeItem(ItemStack stack) {
        this.representativeItem = stack;
    }

    public String getModId() {
        return modId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }

    public List<ItemStack> getItems() {
        return Collections.unmodifiableList(items);
    }

    public int getItemCount() {
        return items.size();
    }

    public ItemStack getRepresentativeItem() {
        if (representativeItem.isEmpty() && !items.isEmpty()) {
            return items.get(0);
        }
        return representativeItem.isEmpty() ? new ItemStack(Items.COMPASS) : representativeItem;
    }
}
