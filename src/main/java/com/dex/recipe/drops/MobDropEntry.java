package com.dex.recipe.drops;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

public class MobDropEntry {
    private final EntityType<?> entityType;
    private final String mobName;
    private final ItemStack dropItem;
    private final String dropChance;
    private final ItemStack representativeIcon;

    public MobDropEntry(EntityType<?> entityType, ItemStack dropItem, String dropChance) {
        this.entityType = entityType;
        this.mobName = entityType.getDescription().getString();
        this.dropItem = dropItem.copy();
        this.dropChance = dropChance != null ? dropChance : "Normal Drop";

        // Find spawn egg or fallback icon
        SpawnEggItem egg = SpawnEggItem.byId(entityType);
        if (egg != null) {
            this.representativeIcon = new ItemStack(egg);
        } else {
            this.representativeIcon = new ItemStack(Items.ZOMBIE_HEAD);
        }
    }

    public EntityType<?> getEntityType() {
        return entityType;
    }

    public String getMobName() {
        return mobName;
    }

    public ItemStack getDropItem() {
        return dropItem;
    }

    public String getDropChance() {
        return dropChance;
    }

    public ItemStack getRepresentativeIcon() {
        return representativeIcon;
    }
}
