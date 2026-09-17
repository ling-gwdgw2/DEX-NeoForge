package com.dex.client.cheat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Handles giving items directly to the player in Creative or OP Cheat Mode.
 */
public final class CheatGiveHelper {

    private CheatGiveHelper() {}

    public static void give(ItemStack stack, boolean fullStack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || stack == null || stack.isEmpty()) return;

        int count = fullStack ? stack.getMaxStackSize() : 1;
        ItemStack toGive = stack.copy();
        toGive.setCount(count);

        if (mc.player.isCreative()) {
            boolean added = mc.player.getInventory().add(toGive);
            if (!added) {
                mc.player.getInventory().setItem(mc.player.getInventory().selected, toGive);
            }
            mc.player.containerMenu.broadcastChanges();
            mc.player.displayClientMessage(
                    Component.literal("DEX [Cheat]: Gave " + count + "x ")
                            .append(toGive.getHoverName())
                            .withStyle(ChatFormatting.GOLD),
                    true
            );
            return;
        }

        // Multiplayer / Server command fallback for OP players
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(toGive.getItem());
        if (id != null && mc.player.connection != null) {
            String cmd = "give @s " + id + " " + count;
            mc.player.connection.sendCommand(cmd);
            mc.player.displayClientMessage(
                    Component.literal("DEX [Cheat]: /give " + count + "x ")
                            .append(toGive.getHoverName())
                            .withStyle(ChatFormatting.GOLD),
                    true
            );
        }
    }
}
