package com.dex.catalog;

import com.dex.DEXMod;
import com.dex.client.bookmark.BookmarkManager;
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
    public static final String BOOKMARKS_MOD_ID = "__bookmarks__";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    public enum SearchTokenType {
        MOD,
        TAG,
        TOOLTIP,
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
                case TOOLTIP -> entry.lowerTooltip.contains(term) || entry.lowerName.contains(term);
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
        public final String lowerTooltip;
        public final Set<String> lowerTags;

        public ItemSearchEntry(ItemStack stack, String lowerName, String lowerId,
                               String lowerModId, String lowerModName, String lowerTooltip, Set<String> lowerTags) {
            this.stack = stack;
            this.lowerName = lowerName;
            this.lowerId = lowerId;
            this.lowerModId = lowerModId;
            this.lowerModName = lowerModName;
            this.lowerTooltip = lowerTooltip;
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

        public int calculateScore(List<SearchToken> tokens, String rawQuery) {
            int score = 0;
            String q = rawQuery.toLowerCase(Locale.ROOT).trim();
            if (lowerName.equals(q)) {
                score += 10000;
            } else if (lowerName.startsWith(q)) {
                score += 5000;
            } else if (lowerName.contains(" " + q)) {
                score += 2500;
            } else if (lowerName.contains(q)) {
                score += 1000;
            } else if (lowerId.contains(q)) {
                score += 400;
            }

            // Direct compact names rank slightly higher than lengthy sub-variants
            score -= Math.min(200, lowerName.length());

            for (SearchToken token : tokens) {
                if (!token.negate()) {
                    String term = token.term();
                    if (lowerName.startsWith(term)) {
                        score += 300;
                    } else if (lowerName.contains(" " + term)) {
                        score += 200;
                    } else if (lowerName.contains(term)) {
                        score += 100;
                    }
                }
            }
            return score;
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
        DEXMod.LOGGER.info("Starting optimized ItemCatalogManager indexing with relevance scoring...");

        modInfoMap.clear();
        sortedModList.clear();
        allItems.clear();
        allSearchEntries.clear();
        searchEntriesByMod.clear();

        // 1. Create special ALL MODS entry
        ModInfo allMods = new ModInfo(ALL_MODS_ID, "All Items", "Show all items across all mods", "");
        allMods.setRepresentativeItem(new ItemStack(Items.CHEST));

        // 2. Create special BOOKMARKS entry
        ModInfo bookmarksMod = new ModInfo(BOOKMARKS_MOD_ID, "★ Bookmarks", "Show pinned and favorite items (Press 'A' to pin)", "");
        bookmarksMod.setRepresentativeItem(new ItemStack(Items.NETHER_STAR));

        // 3. Iterate through BuiltInRegistries.ITEM
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
            String lowerTooltip = (lowerName + " " + lowerId + " " + stack.getDescriptionId()).toLowerCase(Locale.ROOT);

            Set<String> lowerTags = new HashSet<>();
            stack.getTags().forEach(tagKey -> {
                ResourceLocation tagLoc = tagKey.location();
                lowerTags.add(tagLoc.toString().toLowerCase(Locale.ROOT));
                lowerTags.add(tagLoc.getPath().toLowerCase(Locale.ROOT));
            });

            ItemSearchEntry entry = new ItemSearchEntry(stack, lowerName, lowerId, lowerModId, lowerModName, lowerTooltip, lowerTags);
            allSearchEntries.add(entry);
            searchEntriesByMod.computeIfAbsent(namespace, k -> new ArrayList<>()).add(entry);
        }

        // 4. Sort mods: Minecraft first, then alphabetically by display name
        sortedModList.addAll(modInfoMap.values());
        sortedModList.sort((a, b) -> {
            if ("minecraft".equalsIgnoreCase(a.getModId())) return -1;
            if ("minecraft".equalsIgnoreCase(b.getModId())) return 1;
            return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
        });

        // Add ALL entry and BOOKMARKS entry at the head of the list
        sortedModList.add(0, bookmarksMod);
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
     * - Tooltip prefix: $sharpness, $speed
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
            } else if (raw.startsWith("$") && raw.length() > 1) {
                type = SearchTokenType.TOOLTIP;
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
     * In-memory filtering and relevance sorting.
     */
    public void updateFilter() {
        List<ItemSearchEntry> pool;
        if (BOOKMARKS_MOD_ID.equals(selectedModId)) {
            pool = new ArrayList<>();
            for (ItemSearchEntry entry : allSearchEntries) {
                if (BookmarkManager.getInstance().isBookmarked(entry.stack)) {
                    pool.add(entry);
                }
            }
        } else if (ALL_MODS_ID.equals(selectedModId)) {
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

        List<ItemSearchEntry> matchingEntries = new ArrayList<>();
        for (ItemSearchEntry entry : pool) {
            if (entry.matchesAll(tokens)) {
                matchingEntries.add(entry);
            }
        }

        // Rank by relevance
        matchingEntries.sort((a, b) -> Integer.compare(
                b.calculateScore(tokens, currentSearchQuery),
                a.calculateScore(tokens, currentSearchQuery)
        ));

        List<ItemStack> filtered = new ArrayList<>(matchingEntries.size());
        for (ItemSearchEntry entry : matchingEntries) {
            filtered.add(entry.stack);
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
