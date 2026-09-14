package com.dex.recipe.trading;

import com.dex.DEXMod;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.*;

public class VillagerTradeIndexManager {
    private static final VillagerTradeIndexManager INSTANCE = new VillagerTradeIndexManager();

    private final List<VillagerTradeEntry> allTrades = new ArrayList<>();
    private final Map<Item, List<VillagerTradeEntry>> tradesByResult = new HashMap<>();
    private final Map<Item, List<VillagerTradeEntry>> tradesByCost = new HashMap<>();

    public static VillagerTradeIndexManager getInstance() {
        return INSTANCE;
    }

    public synchronized void reindex() {
        allTrades.clear();
        tradesByResult.clear();
        tradesByCost.clear();

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        RandomSource random = RandomSource.create(42);

        // 1. Villager Profession Trades
        for (Map.Entry<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> entry : VillagerTrades.TRADES.entrySet()) {
            VillagerProfession prof = entry.getKey();
            ResourceLocation profKey = BuiltInRegistries.VILLAGER_PROFESSION.getKey(prof);
            String profName = profKey != null ? formatName(profKey.getPath()) : "Villager";

            Int2ObjectMap<VillagerTrades.ItemListing[]> levelMap = entry.getValue();
            for (int level = 1; level <= 5; level++) {
                VillagerTrades.ItemListing[] listings = levelMap.get(level);
                if (listings == null) continue;

                for (VillagerTrades.ItemListing listing : listings) {
                    try {
                        MerchantOffer offer = listing.getOffer(player, random);
                        if (offer != null && !offer.getResult().isEmpty()) {
                            addTrade(new VillagerTradeEntry(profName, level, offer.getCostA(), offer.getCostB(), offer.getResult()));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // 2. Wandering Trader Trades
        if (VillagerTrades.WANDERING_TRADER_TRADES != null) {
            for (int level : VillagerTrades.WANDERING_TRADER_TRADES.keySet()) {
                VillagerTrades.ItemListing[] listings = VillagerTrades.WANDERING_TRADER_TRADES.get(level);
                if (listings == null) continue;

                for (VillagerTrades.ItemListing listing : listings) {
                    try {
                        MerchantOffer offer = listing.getOffer(player, random);
                        if (offer != null && !offer.getResult().isEmpty()) {
                            addTrade(new VillagerTradeEntry("Wandering Trader", level, offer.getCostA(), offer.getCostB(), offer.getResult()));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        DEXMod.LOGGER.info("DEX Villager Trade Indexing completed: {} trades indexed.", allTrades.size());
    }

    private void addTrade(VillagerTradeEntry trade) {
        allTrades.add(trade);
        tradesByResult.computeIfAbsent(trade.getResult().getItem(), k -> new ArrayList<>()).add(trade);
        if (!trade.getCostA().isEmpty()) {
            tradesByCost.computeIfAbsent(trade.getCostA().getItem(), k -> new ArrayList<>()).add(trade);
        }
        if (!trade.getCostB().isEmpty()) {
            tradesByCost.computeIfAbsent(trade.getCostB().getItem(), k -> new ArrayList<>()).add(trade);
        }
    }

    public List<VillagerTradeEntry> getTradesSelling(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        return tradesByResult.getOrDefault(stack.getItem(), Collections.emptyList());
    }

    public List<VillagerTradeEntry> getTradesBuying(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        return tradesByCost.getOrDefault(stack.getItem(), Collections.emptyList());
    }

    public boolean hasTrades(ItemStack stack) {
        return !getTradesSelling(stack).isEmpty() || !getTradesBuying(stack).isEmpty();
    }

    private String formatName(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1))
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
