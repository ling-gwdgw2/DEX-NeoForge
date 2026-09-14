package com.dex.catalog;

import com.dex.DEXMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemCatalogManager {
    private static final ItemCatalogManager INSTANCE = new ItemCatalogManager();

    public static final String ALL_MODS_ID = "__all__";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    public enum SearchTokenType {
        MOD,
        TAG,
        NAME_OR_ID
    }

    public record SearchToken(SearchTokenType type, String term, boolean negate) {
        public boolean matches(ItemSearchEntry entry) {
            if (term.isEmpty()) return true;

            boolean matched = switch (type) {
                case MOD -> entry.lowerModId.contains(term) || entry.lowerModName.contains(term);
                case TAG -> {
                    for (String tag : entry.lowerTags) {
                        if (tag.contains(term)) yield true;
                    }
                    yield false;
                }
                case NAME_OR_ID -> entry.lowerName.contains(term) || entry.lowerId.contains(term);
            };

            return negate != matched;
        }
    }

    public static class ItemSearchEntry {
        public final ItemStack stack;
        public final String lowerName;
        public final String lowerId;
        public final String lowerModId;
        public final String lowerModName;
        public final Set<String> lowerTags;

        public ItemSearchEntry(ItemStack stack, String lowerName, String lowerId,
                               String lowerModId, String lowerModName, Set<String> lowerTags) {
            this.stack = stack;
            this.lowerName = lowerName;
            this.lowerId = lowerId;
            this.lowerModId = lowerModId;
            this.lowerModName = lowerModName;
            this.lowerTags = lowerTags;
        }

        public boolean matchesAll(List<SearchToken> tokens) {
            for (SearchToken token : tokens) {
                if (!token.matches(this)) {
                    return false;
                }
            }
            return true;
        }
    }

    private final Map<String, ModInfo> modInfoMap = new LinkedHashMap<>();
    private final List<ModInfo> sortedModList = new ArrayList<>();
    private final List<ItemStack> allItems = new ArrayList<>();
    private final List<ItemSearchEntry> allSearchEntries = new ArrayList<>();
    private final Map<String, List<ItemSearchEntry>> searchEntriesByMod = new HashMap<>();

    private boolean initialized = false;

    private String selectedModId = ALL_MODS_ID;
    private String currentSearchQuery = "";
    private List<ItemStack> currentFilteredItems = new ArrayList<>();

    public static ItemCatalogManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (initialized) return;

        long startTime = System.currentTimeMillis();
        DEXMod.LOGGER.info("Starting optimized ItemCatalogManager indexing...");

        modInfoMap.clear();
        sortedModList.clear();
        allItems.clear();
        allSearchEntries.clear();
        searchEntriesByMod.clear();

        // 1. Create special ALL MODS entry
        ModInfo allMods = new ModInfo(ALL_MODS_ID, "All Items", "Show all items across all mods", "");
        allMods.setRepresentativeItem(new ItemStack(Items.CHEST));

        // 2. Iterate through BuiltInRegistries.ITEM
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;

            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            String namespace = key.getNamespace();

            ModInfo mod = modInfoMap.computeIfAbsent(namespace, ns -> {
                String displayName = ns;
                String description = "";
                String version = "";

                if ("minecraft".equals(ns)) {
                    displayName = "Minecraft";
                    description = "Vanilla Minecraft items and blocks";
                } else if ("c".equals(ns) || "forge".equals(ns) || "neoforge".equals(ns)) {
                    displayName = "Common / Tags";
                } else if (ModList.get().isLoaded(ns)) {
                    var containerOpt = ModList.get().getModContainerById(ns);
                    if (containerOpt.isPresent()) {
                        IModInfo info = containerOpt.get().getModInfo();
                        displayName = info.getDisplayName();
                        description = info.getDescription();
                        version = info.getVersion().toString();
                    }
                }

                ModInfo newMod = new ModInfo(ns, displayName, description, version);
                if ("minecraft".equals(ns)) {
                    newMod.setRepresentativeItem(new ItemStack(Items.GRASS_BLOCK));
                }
                return newMod;
            });

            ItemStack stack = new ItemStack(item);
            mod.addItem(stack);
            allItems.add(stack);

            // Precompute search metadata for instant O(1) filtering
            String lowerName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            String lowerId = key.toString().toLowerCase(Locale.ROOT);
            String lowerModId = namespace.toLowerCase(Locale.ROOT);
            String lowerModName = mod.getDisplayName().toLowerCase(Locale.ROOT);

            Set<String> lowerTags = new HashSet<>();
            stack.getTags().forEach(tagKey -> {
                ResourceLocation tagLoc = tagKey.location();
                lowerTags.add(tagLoc.toString().toLowerCase(Locale.ROOT));
                lowerTags.add(tagLoc.getPath().toLowerCase(Locale.ROOT));
            });

            ItemSearchEntry entry = new ItemSearchEntry(stack, lowerName, lowerId, lowerModId, lowerModName, lowerTags);
            allSearchEntries.add(entry);
            searchEntriesByMod.computeIfAbsent(namespace, k -> new ArrayList<>()).add(entry);
        }

        // 3. Sort mods: Minecraft first, then alphabetically by display name
        sortedModList.addAll(modInfoMap.values());
        sortedModList.sort((a, b) -> {
            if ("minecraft".equalsIgnoreCase(a.getModId())) return -1;
            if ("minecraft".equalsIgnoreCase(b.getModId())) return 1;
            return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
        });

        // Add ALL entry at the head of the list
        sortedModList.add(0, allMods);

        initialized = true;
        long duration = System.currentTimeMillis() - startTime;
        DEXMod.LOGGER.info("ItemCatalogManager indexed {} mods and {} total items in {}ms.",
                modInfoMap.size(), allItems.size(), duration);

        updateFilter();
    }

    public void selectMod(String modId) {
        this.selectedModId = modId != null ? modId : ALL_MODS_ID;
        updateFilter();
    }

    public String getSelectedModId() {
        return selectedModId;
    }

    public void setSearchQuery(String query) {
        this.currentSearchQuery = query != null ? query.trim() : "";
        updateFilter();
    }

    public String getSearchQuery() {
        return currentSearchQuery;
    }

    /**
     * Parses the search query string into a list of SearchTokens.
     * Supports:
     * - Multi-word tokens: "iron sword"
     * - Mod prefix: @create, @minecraft
     * - Tag prefix: #c:ingots, #ores
     * - Negation prefix: -ingot, -#c:plates, -@minecraft
     * - Exact quotes: "raw iron"
     */
    public List<SearchToken> parseQuery(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<SearchToken> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(query);

        while (matcher.find()) {
            String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (raw == null || raw.isBlank()) continue;

            boolean negate = false;
            if (raw.startsWith("-") && raw.length() > 1) {
                negate = true;
                raw = raw.substring(1);
            }

            SearchTokenType type = SearchTokenType.NAME_OR_ID;
            String term = raw;

            if (raw.startsWith("@") && raw.length() > 1) {
                type = SearchTokenType.MOD;
                term = raw.substring(1);
            } else if (raw.startsWith("#") && raw.length() > 1) {
                type = SearchTokenType.TAG;
                term = raw.substring(1);
            }

            term = term.toLowerCase(Locale.ROOT).trim();
            if (!term.isEmpty()) {
                tokens.add(new SearchToken(type, term, negate));
            }
        }

        return tokens;
    }

    /**
     * Instantaneous in-memory filtering across precomputed search entries.
     */
    public void updateFilter() {
        List<ItemSearchEntry> pool;
        if (ALL_MODS_ID.equals(selectedModId)) {
            pool = allSearchEntries;
        } else {
            pool = searchEntriesByMod.getOrDefault(selectedModId, Collections.emptyList());
        }

        if (currentSearchQuery.isEmpty()) {
            List<ItemStack> list = new ArrayList<>(pool.size());
            for (ItemSearchEntry entry : pool) {
                list.add(entry.stack);
            }
            currentFilteredItems = list;
            return;
        }

        List<SearchToken> tokens = parseQuery(currentSearchQuery);
        if (tokens.isEmpty()) {
            List<ItemStack> list = new ArrayList<>(pool.size());
            for (ItemSearchEntry entry : pool) {
                list.add(entry.stack);
            }
            currentFilteredItems = list;
            return;
        }

        List<ItemStack> filtered = new ArrayList<>();
        for (ItemSearchEntry entry : pool) {
            if (entry.matchesAll(tokens)) {
                filtered.add(entry.stack);
            }
        }

        currentFilteredItems = filtered;
    }

    public List<ItemStack> getCurrentFilteredItems() {
        return currentFilteredItems;
    }

    public List<ItemStack> getAllItems() {
        return Collections.unmodifiableList(allItems);
    }

    public int getTotalItemCount() {
        return allItems.size();
    }

    public List<ModInfo> getSortedModList() {
        return sortedModList;
    }

    public ModInfo getModInfo(String modId) {
        return modInfoMap.get(modId);
    }
}
