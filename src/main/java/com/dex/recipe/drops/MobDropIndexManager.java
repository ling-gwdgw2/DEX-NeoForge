package com.dex.recipe.drops;

import com.dex.DEXMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public class MobDropIndexManager {
    private static final MobDropIndexManager INSTANCE = new MobDropIndexManager();

    private final List<MobDropEntry> allDrops = new ArrayList<>();
    private final Map<Item, List<MobDropEntry>> dropsByItem = new HashMap<>();

    public static MobDropIndexManager getInstance() {
        return INSTANCE;
    }

    public synchronized void reindex() {
        allDrops.clear();
        dropsByItem.clear();

        // 1. Common / Iconical Mob Drops
        add(EntityType.ENDERMAN, Items.ENDER_PEARL, "0-1 (Common)");
        add(EntityType.BLAZE, Items.BLAZE_ROD, "0-1 (50%)");
        add(EntityType.SKELETON, Items.BONE, "0-2 (Common)");
        add(EntityType.SKELETON, Items.ARROW, "0-2 (Common)");
        add(EntityType.WITHER_SKELETON, Items.WITHER_SKELETON_SKULL, "2.5% (Rare)");
        add(EntityType.WITHER_SKELETON, Items.BONE, "0-2 (Common)");
        add(EntityType.WITHER_SKELETON, Items.COAL, "0-1 (Common)");
        add(EntityType.CREEPER, Items.GUNPOWDER, "0-2 (Common)");
        add(EntityType.ZOMBIE, Items.ROTTEN_FLESH, "0-2 (Common)");
        add(EntityType.ZOMBIE, Items.IRON_INGOT, "0.83% (Rare)");
        add(EntityType.DROWNED, Items.NAUTILUS_SHELL, "3% (Rare)");
        add(EntityType.DROWNED, Items.TRIDENT, "2% (Rare)");
        add(EntityType.SPIDER, Items.STRING, "0-2 (Common)");
        add(EntityType.SPIDER, Items.SPIDER_EYE, "0-1 (33%)");
        add(EntityType.CAVE_SPIDER, Items.STRING, "0-2 (Common)");
        add(EntityType.CAVE_SPIDER, Items.SPIDER_EYE, "0-1 (33%)");
        add(EntityType.SLIME, Items.SLIME_BALL, "0-2 (Common)");
        add(EntityType.MAGMA_CUBE, Items.MAGMA_CREAM, "0-1 (25%)");
        add(EntityType.GHAST, Items.GHAST_TEAR, "0-1 (50%)");
        add(EntityType.GHAST, Items.GUNPOWDER, "0-2 (Common)");
        add(EntityType.WITCH, Items.REDSTONE, "0-6 (Common)");
        add(EntityType.WITCH, Items.GLOWSTONE_DUST, "0-6 (Common)");
        add(EntityType.WITCH, Items.SUGAR, "0-6 (Common)");
        add(EntityType.WITCH, Items.GLASS_BOTTLE, "0-6 (Common)");
        add(EntityType.WITCH, Items.GUNPOWDER, "0-6 (Common)");
        add(EntityType.BREEZE, Items.BREEZE_ROD, "1-2 (Common)");
        add(EntityType.WARDEN, Items.SCULK_CATALYST, "100% (Guaranteed)");
        add(EntityType.GUARDIAN, Items.PRISMARINE_SHARD, "0-2 (Common)");
        add(EntityType.GUARDIAN, Items.PRISMARINE_CRYSTALS, "0-1 (Rare)");
        add(EntityType.GUARDIAN, Items.COD, "0-1 (Common)");
        add(EntityType.ELDER_GUARDIAN, Items.WET_SPONGE, "100% (Guaranteed)");
        add(EntityType.SHULKER, Items.SHULKER_SHELL, "0-1 (50%)");
        add(EntityType.PHANTOM, Items.PHANTOM_MEMBRANE, "0-1 (Common)");
        add(EntityType.EVOKER, Items.TOTEM_OF_UNDYING, "100% (Guaranteed)");
        add(EntityType.WITHER, Items.NETHER_STAR, "100% (Guaranteed)");
        add(EntityType.COW, Items.LEATHER, "0-2 (Common)");
        add(EntityType.COW, Items.BEEF, "1-3 (Common)");
        add(EntityType.PIG, Items.PORKCHOP, "1-3 (Common)");
        add(EntityType.SHEEP, Items.WHITE_WOOL, "1 (Common)");
        add(EntityType.SHEEP, Items.MUTTON, "1-2 (Common)");
        add(EntityType.CHICKEN, Items.FEATHER, "0-2 (Common)");
        add(EntityType.CHICKEN, Items.CHICKEN, "1 (Common)");
        add(EntityType.RABBIT, Items.RABBIT_HIDE, "0-1 (Common)");
        add(EntityType.RABBIT, Items.RABBIT, "1 (Common)");
        add(EntityType.RABBIT, Items.RABBIT_FOOT, "10% (Rare)");
        add(EntityType.SQUID, Items.INK_SAC, "1-3 (Common)");
        add(EntityType.GLOW_SQUID, Items.GLOW_INK_SAC, "1-3 (Common)");
        add(EntityType.IRON_GOLEM, Items.IRON_INGOT, "3-5 (Guaranteed)");
        add(EntityType.IRON_GOLEM, Items.POPPY, "0-2 (Common)");

        DEXMod.LOGGER.info("DEX Mob Drop Indexing completed: {} mob drop mappings indexed.", allDrops.size());
    }

    private void add(EntityType<?> type, Item dropItem, String chance) {
        MobDropEntry entry = new MobDropEntry(type, new ItemStack(dropItem), chance);
        allDrops.add(entry);
        dropsByItem.computeIfAbsent(dropItem, k -> new ArrayList<>()).add(entry);
    }

    public List<MobDropEntry> getDropsFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        return dropsByItem.getOrDefault(stack.getItem(), Collections.emptyList());
    }

    public boolean hasMobDrops(ItemStack stack) {
        return !getDropsFor(stack).isEmpty();
    }
}
