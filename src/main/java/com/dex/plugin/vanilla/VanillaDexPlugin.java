package com.dex.plugin.vanilla;

import com.dex.api.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Default DEX plugin providing vanilla Minecraft recipe categories, workstations, and integration.
 */
@DexPlugin
public class VanillaDexPlugin implements IDexPlugin {
    public static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
    public static final ResourceLocation SMELTING = ResourceLocation.fromNamespaceAndPath("minecraft", "smelting");
    public static final ResourceLocation SMITHING = ResourceLocation.fromNamespaceAndPath("minecraft", "smithing");
    public static final ResourceLocation BREWING = ResourceLocation.fromNamespaceAndPath("minecraft", "brewing");
    public static final ResourceLocation TRADING = ResourceLocation.fromNamespaceAndPath("minecraft", "villager_trading");
    public static final ResourceLocation MOB_DROPS = ResourceLocation.fromNamespaceAndPath("minecraft", "mob_drops");

    @Override
    public void registerCategories(IDexCategoryRegistry registry) {
        // 1. Crafting Table Category
        registry.registerCategory(new BaseDexCategory<>(
                CRAFTING,
                Component.literal("Crafting Table"),
                new ItemStack(Blocks.CRAFTING_TABLE)
        ));

        // 2. Smelting / Cooking Category
        registry.registerCategory(new BaseDexCategory<>(
                SMELTING,
                Component.literal("Furnace & Smelting"),
                new ItemStack(Blocks.FURNACE)
        ));

        // 3. Smithing Table Category
        registry.registerCategory(new BaseDexCategory<>(
                SMITHING,
                Component.literal("Smithing Table"),
                new ItemStack(Blocks.SMITHING_TABLE)
        ));

        // 4. Brewing Stand Category
        registry.registerCategory(new BaseDexCategory<>(
                BREWING,
                Component.literal("Brewing Stand"),
                new ItemStack(Blocks.BREWING_STAND)
        ));

        // 5. Villager Trading Category
        registry.registerCategory(new BaseDexCategory<>(
                TRADING,
                Component.literal("Villager Trading"),
                new ItemStack(Items.EMERALD)
        ));

        // 6. Mob Drops Category
        registry.registerCategory(new BaseDexCategory<>(
                MOB_DROPS,
                Component.literal("Mob Drops"),
                new ItemStack(Items.BONE)
        ));
    }

    @Override
    public void registerWorkstations(IDexWorkstationRegistry registry) {
        // Workstation mappings
        registry.registerWorkstation(CRAFTING, new ItemStack(Blocks.CRAFTING_TABLE));
        registry.registerWorkstation(SMELTING, new ItemStack(Blocks.FURNACE));
        registry.registerWorkstation(SMELTING, new ItemStack(Blocks.BLAST_FURNACE));
        registry.registerWorkstation(SMELTING, new ItemStack(Blocks.SMOKER));
        registry.registerWorkstation(SMITHING, new ItemStack(Blocks.SMITHING_TABLE));
        registry.registerWorkstation(BREWING, new ItemStack(Blocks.BREWING_STAND));
    }

    @Override
    public void registerRecipes(IDexRecipeRegistry registry, ClientLevel level) {
        // Built-in recipes are handled by DEX's index managers
    }

    /**
     * Helper record for standard recipe categories.
     */
    public record BaseDexCategory<T>(ResourceLocation id, Component title, ItemStack icon) implements IDexRecipeCategory<T> {
        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public Component getTitle() {
            return title;
        }

        @Override
        public ItemStack getIcon() {
            return icon;
        }
    }
}
