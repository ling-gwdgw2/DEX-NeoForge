package com.dex.recipe.tree;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class CraftingTreeNode {
    private final ItemStack item;
    private final int count;
    private final boolean isBaseMaterial;
    private final List<CraftingTreeNode> children = new ArrayList<>();

    public CraftingTreeNode(ItemStack item, int count, boolean isBaseMaterial) {
        this.item = item.copy();
        this.count = count;
        this.isBaseMaterial = isBaseMaterial;
    }

    public void addChild(CraftingTreeNode child) {
        this.children.add(child);
    }

    public ItemStack getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public boolean isBaseMaterial() {
        return isBaseMaterial;
    }

    public List<CraftingTreeNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public Map<Item, Integer> getRawMaterialsSummary() {
        Map<Item, Integer> summary = new LinkedHashMap<>();
        collectRawMaterials(summary);
        return summary;
    }

    private void collectRawMaterials(Map<Item, Integer> summary) {
        if (children.isEmpty() || isBaseMaterial) {
            summary.merge(item.getItem(), count, Integer::sum);
        } else {
            for (CraftingTreeNode child : children) {
                child.collectRawMaterials(summary);
            }
        }
    }
}
