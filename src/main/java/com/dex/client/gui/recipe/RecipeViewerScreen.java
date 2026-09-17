package com.dex.client.gui.recipe;

import com.dex.api.DexRecipeSlot;
import com.dex.api.IDexRecipeCategory;
import com.dex.catalog.ItemCatalogManager;
import com.dex.catalog.ModInfo;
import com.dex.client.bookmark.BookmarkManager;
import com.dex.client.config.DEXConfig;
import com.dex.client.util.DexSoundHelper;
import com.dex.plugin.DexPluginManager;
import com.dex.plugin.DexRegistriesImpl;
import com.dex.recipe.RecipeIndexManager;
import com.dex.recipe.RecipeIngredientHelper;
import com.dex.recipe.brewing.BrewingIndexManager;
import com.dex.recipe.brewing.BrewingRecipeEntry;
import com.dex.recipe.drops.MobDropEntry;
import com.dex.recipe.drops.MobDropIndexManager;
import com.dex.recipe.info.ItemInfoRegistry;
import com.dex.recipe.trading.VillagerTradeEntry;
import com.dex.recipe.trading.VillagerTradeIndexManager;
import com.dex.recipe.transfer.RecipeTransferHelper;
import com.dex.recipe.tree.CraftingTreeCalculator;
import com.dex.recipe.tree.CraftingTreeNode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class RecipeViewerScreen extends Screen {
    public enum Mode {
        CRAFTING,
        USAGE
    }

    public record HistoryEntry(ItemStack targetItem, Mode mode, String activeTabId, int recipeIndex) {}
    private static final Deque<HistoryEntry> HISTORY = new ArrayDeque<>();

    public static class CategoryTab {
        public final String id;
        public final String label;
        public final ItemStack icon;
        public final int recipeCount;
        public final IDexRecipeCategory<?> customCategory;

        public CategoryTab(String id, String label, ItemStack icon, int recipeCount, IDexRecipeCategory<?> customCategory) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.recipeCount = recipeCount;
            this.customCategory = customCategory;
        }
    }

    private final Screen previousScreen;
    private final ItemStack targetItem;
    private final Mode mode;

    private final List<RecipeHolder<?>> allHolders;
    private final Map<String, List<RecipeHolder<?>>> recipesByCategory = new LinkedHashMap<>();
    private final Map<String, RecipeIngredientHelper.RecipeCategoryInfo> categoryMeta = new LinkedHashMap<>();

    private final List<BrewingRecipeEntry> brewingRecipes;
    private final List<VillagerTradeEntry> villagerTrades;
    private final List<MobDropEntry> mobDrops;
    private final Map<IDexRecipeCategory<?>, List<DexRegistriesImpl.CustomRecipeEntry>> customCategories = new LinkedHashMap<>();

    private final List<CategoryTab> tabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private int categoryTabOffset = 0;
    private final Map<String, Integer> tabRecipeIndices = new HashMap<>();

    // Tree calculator multiplier
    private int treeMultiplier = 1;
    private CraftingTreeNode cachedTree = null;

    private int guiLeft;
    private int guiTop;
    private final int guiWidth = 260;
    private final int guiHeight = 195;

    private ItemStack hoveredSlotItem = ItemStack.EMPTY;
    private boolean hoveredSlotIsMissing = false;

    public RecipeViewerScreen(Screen previousScreen, ItemStack targetItem, Mode mode) {
        super(Component.literal(mode == Mode.CRAFTING ? "Recipe Viewer" : "Usage Viewer"));
        this.previousScreen = previousScreen;
        this.targetItem = targetItem.copy();
        this.mode = mode;

        if (mode == Mode.CRAFTING) {
            this.allHolders = new ArrayList<>(RecipeIndexManager.getInstance().getRecipesFor(targetItem));
            this.brewingRecipes = new ArrayList<>(BrewingIndexManager.getInstance().getRecipesFor(targetItem));
            this.villagerTrades = new ArrayList<>(VillagerTradeIndexManager.getInstance().getTradesSelling(targetItem));
            this.mobDrops = new ArrayList<>(MobDropIndexManager.getInstance().getDropsFor(targetItem));

            List<DexRegistriesImpl.CustomRecipeEntry> customEntries =
                    DexPluginManager.getInstance().getRegistries().getCustomRecipesForOutput(targetItem);
            for (DexRegistriesImpl.CustomRecipeEntry entry : customEntries) {
                this.customCategories.computeIfAbsent(entry.category(), k -> new ArrayList<>()).add(entry);
            }
        } else {
            this.allHolders = new ArrayList<>(RecipeIndexManager.getInstance().getUsagesFor(targetItem));
            this.brewingRecipes = new ArrayList<>(BrewingIndexManager.getInstance().getUsagesFor(targetItem));
            this.villagerTrades = new ArrayList<>(VillagerTradeIndexManager.getInstance().getTradesBuying(targetItem));
            this.mobDrops = new ArrayList<>();

            List<DexRegistriesImpl.CustomRecipeEntry> customEntries =
                    DexPluginManager.getInstance().getRegistries().getCustomRecipesForInput(targetItem);
            for (DexRegistriesImpl.CustomRecipeEntry entry : customEntries) {
                this.customCategories.computeIfAbsent(entry.category(), k -> new ArrayList<>()).add(entry);
            }
        }

        // Group standard and modded RecipeManager recipes by workstation category
        for (RecipeHolder<?> holder : allHolders) {
            RecipeIngredientHelper.RecipeCategoryInfo info = RecipeIngredientHelper.getCategoryInfo(holder);
            recipesByCategory.computeIfAbsent(info.id(), k -> new ArrayList<>()).add(holder);
            categoryMeta.putIfAbsent(info.id(), info);
        }

        buildTabs();
        this.cachedTree = CraftingTreeCalculator.calculateTree(targetItem, treeMultiplier);
    }

    private void buildTabs() {
        tabs.clear();

        // 1. Workstation & Recipe Category Tabs
        for (Map.Entry<String, List<RecipeHolder<?>>> entry : recipesByCategory.entrySet()) {
            String catId = entry.getKey();
            List<RecipeHolder<?>> list = entry.getValue();
            RecipeIngredientHelper.RecipeCategoryInfo info = categoryMeta.get(catId);
            String title = info != null ? info.title().getString() : catId;
            ItemStack icon = info != null ? info.icon() : new ItemStack(Items.CRAFTING_TABLE);
            tabs.add(new CategoryTab(catId, title, icon, list.size(), null));
        }

        if (tabs.isEmpty() && brewingRecipes.isEmpty() && villagerTrades.isEmpty() && mobDrops.isEmpty() && customCategories.isEmpty()) {
            tabs.add(new CategoryTab("crafting", "Crafting", new ItemStack(Items.CRAFTING_TABLE), 0, null));
        }

        // 2. Brewing Tab
        if (!brewingRecipes.isEmpty()) {
            tabs.add(new CategoryTab("brewing", "Brewing", new ItemStack(Items.BREWING_STAND), brewingRecipes.size(), null));
        }

        // 3. Trading Tab
        if (!villagerTrades.isEmpty()) {
            tabs.add(new CategoryTab("trading", "Trading", new ItemStack(Items.EMERALD), villagerTrades.size(), null));
        }

        // 4. Mob Drops Tab
        if (!mobDrops.isEmpty()) {
            tabs.add(new CategoryTab("drops", "Mob Drops", new ItemStack(Items.BONE), mobDrops.size(), null));
        }

        // 5. Crafting Tree Tab
        if (mode == Mode.CRAFTING && !allHolders.isEmpty()) {
            tabs.add(new CategoryTab("tree", "Crafting Tree", new ItemStack(Items.OAK_SAPLING), 1, null));
        }

        // 6. Custom Machine & JEI Plugin Categories
        for (Map.Entry<IDexRecipeCategory<?>, List<DexRegistriesImpl.CustomRecipeEntry>> entry : customCategories.entrySet()) {
            IDexRecipeCategory<?> category = entry.getKey();
            List<DexRegistriesImpl.CustomRecipeEntry> entries = entry.getValue();
            String title = category.getTitle() != null ? category.getTitle().getString() : category.getId().getPath();
            ItemStack icon = category.getIcon() != null && !category.getIcon().isEmpty() ? category.getIcon() : new ItemStack(Items.FURNACE);
            tabs.add(new CategoryTab(category.getId().toString(), title, icon, entries.size(), category));
        }

        // 7. Information & Guide Tab (Captured from JEI / DEX plugins)
        if (ItemInfoRegistry.getInstance().hasInfo(targetItem.getItem())) {
            tabs.add(new CategoryTab("info", "Item Guide", new ItemStack(Items.WRITABLE_BOOK), 1, null));
        }

        if (activeTabIndex >= tabs.size()) {
            activeTabIndex = 0;
        }
    }

    public HistoryEntry saveState() {
        String tabId = !tabs.isEmpty() ? tabs.get(activeTabIndex).id : "crafting";
        int currentIdx = tabRecipeIndices.getOrDefault(tabId, 0);
        return new HistoryEntry(targetItem, mode, tabId, currentIdx);
    }

    public void restoreState(HistoryEntry entry) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id.equals(entry.activeTabId())) {
                this.activeTabIndex = i;
                this.tabRecipeIndices.put(entry.activeTabId(), entry.recipeIndex());
                break;
            }
        }
    }

    public static void openRecipes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RecipeViewerScreen current) {
            HISTORY.push(current.saveState());
        }
        mc.setScreen(new RecipeViewerScreen(mc.screen, stack, Mode.CRAFTING));
    }

    public static void openUsages(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RecipeViewerScreen current) {
            HISTORY.push(current.saveState());
        }
        mc.setScreen(new RecipeViewerScreen(mc.screen, stack, Mode.USAGE));
    }

    private void goBack() {
        Minecraft mc = Minecraft.getInstance();
        if (!HISTORY.isEmpty()) {
            HistoryEntry prev = HISTORY.pop();
            RecipeViewerScreen screen = new RecipeViewerScreen(previousScreen, prev.targetItem(), prev.mode());
            screen.restoreState(prev);
            mc.setScreen(screen);
        } else {
            mc.setScreen(previousScreen);
        }
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - this.guiWidth) / 2;
        this.guiTop = (this.height - this.guiHeight) / 2;

        rebuildCategoryButtons();
    }

    private void rebuildCategoryButtons() {
        this.clearWidgets();

        // 1. Back Button (Left side of Row 1)
        if (!HISTORY.isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.literal("⮌ Back"), b -> {
                DexSoundHelper.playButtonClick();
                goBack();
            }).bounds(guiLeft + 6, guiTop + 5, 44, 14).build());
        }

        // 2. Icon-Driven Category Tabs at Dedicated Row 2 (y = guiTop + 20) with Pagination
        int tabY = guiTop + 20;
        int tabWidth = 24;
        int tabHeight = 20;

        boolean needsPagination = tabs.size() > 9;
        int visibleTabCount = needsPagination ? 8 : 9;

        // Keep activeTabIndex visible within the pagination window
        if (activeTabIndex < categoryTabOffset) {
            categoryTabOffset = activeTabIndex;
        } else if (activeTabIndex >= categoryTabOffset + visibleTabCount) {
            categoryTabOffset = activeTabIndex - visibleTabCount + 1;
        }
        categoryTabOffset = Math.max(0, Math.min(Math.max(0, tabs.size() - visibleTabCount), categoryTabOffset));

        int tabStartX = guiLeft + 8;
        if (needsPagination) {
            tabStartX = guiLeft + 20; // Room for ◀ button

            // Left pagination button ◀
            boolean canScrollLeft = categoryTabOffset > 0;
            Button leftPageBtn = new Button(guiLeft + 6, tabY, 12, tabHeight, Component.literal("◀"), b -> {
                if (categoryTabOffset > 0) {
                    categoryTabOffset--;
                    DexSoundHelper.playButtonClick();
                    rebuildCategoryButtons();
                }
            }, supplier -> supplier.get()) {
                @Override
                public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    int bx = getX();
                    int by = getY();
                    int bw = getWidth();
                    int bh = getHeight();
                    boolean hovered = isHoveredOrFocused() && canScrollLeft;
                    graphics.fill(bx, by, bx + bw, by + bh, hovered ? 0xFF363646 : 0xFF22222C);
                    graphics.renderOutline(bx, by, bw, bh, hovered ? 0xFF888899 : 0xFF444455);
                    graphics.drawCenteredString(font, "◀", bx + bw / 2, by + 6, canScrollLeft ? (hovered ? 0xFFFFAA00 : 0xFFE0E0E0) : 0xFF555566);
                }
            };
            leftPageBtn.active = canScrollLeft;
            leftPageBtn.setTooltip(Tooltip.create(Component.literal("Previous Categories")));
            this.addRenderableWidget(leftPageBtn);

            // Right pagination button ▶
            boolean canScrollRight = categoryTabOffset + visibleTabCount < tabs.size();
            Button rightPageBtn = new Button(guiLeft + guiWidth - 18, tabY, 12, tabHeight, Component.literal("▶"), b -> {
                if (categoryTabOffset + visibleTabCount < tabs.size()) {
                    categoryTabOffset++;
                    DexSoundHelper.playButtonClick();
                    rebuildCategoryButtons();
                }
            }, supplier -> supplier.get()) {
                @Override
                public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    int bx = getX();
                    int by = getY();
                    int bw = getWidth();
                    int bh = getHeight();
                    boolean hovered = isHoveredOrFocused() && canScrollRight;
                    graphics.fill(bx, by, bx + bw, by + bh, hovered ? 0xFF363646 : 0xFF22222C);
                    graphics.renderOutline(bx, by, bw, bh, hovered ? 0xFF888899 : 0xFF444455);
                    graphics.drawCenteredString(font, "▶", bx + bw / 2, by + 6, canScrollRight ? (hovered ? 0xFFFFAA00 : 0xFFE0E0E0) : 0xFF555566);
                }
            };
            rightPageBtn.active = canScrollRight;
            int remaining = Math.max(0, tabs.size() - (categoryTabOffset + visibleTabCount));
            rightPageBtn.setTooltip(Tooltip.create(Component.literal(remaining > 0 ? "More Categories (" + remaining + ")" : "Next Categories")));
            this.addRenderableWidget(rightPageBtn);
        }

        int tabX = tabStartX;
        int endIndex = Math.min(tabs.size(), categoryTabOffset + visibleTabCount);

        for (int i = categoryTabOffset; i < endIndex; i++) {
            CategoryTab tab = tabs.get(i);
            int tabIndex = i;
            boolean isActive = (i == activeTabIndex);

            Button tabButton = new Button(tabX, tabY, tabWidth, tabHeight, Component.literal(tab.label), b -> {
                DexSoundHelper.playButtonClick();
                this.activeTabIndex = tabIndex;
                rebuildCategoryButtons();
            }, supplier -> supplier.get()) {
                @Override
                public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    int bx = getX();
                    int by = getY();
                    int bw = getWidth();
                    int bh = getHeight();

                    if (isActive) {
                        // Elevated modern slate background + gold border + gold bottom line
                        graphics.fill(bx, by, bx + bw, by + bh, 0xFF363646);
                        graphics.renderOutline(bx, by, bw, bh, 0xFFFFAA00);
                        graphics.fill(bx, by + bh - 2, bx + bw, by + bh, 0xFFFFAA00);
                    } else {
                        int bgColor = isHoveredOrFocused() ? 0xFF2E2E3C : 0xFF22222C;
                        graphics.fill(bx, by, bx + bw, by + bh, bgColor);
                        graphics.renderOutline(bx, by, bw, bh, isHoveredOrFocused() ? 0xFF666677 : 0xFF444455);
                    }

                    // Render centered 16x16 tab icon at bx + 4, by + 2
                    if (!tab.icon.isEmpty()) {
                        graphics.renderItem(tab.icon, bx + 4, by + 2);
                    }
                }
            };

            String countStr = tab.recipeCount > 1 ? " (" + tab.recipeCount + " recipes)" : (tab.recipeCount == 1 ? " (1 recipe)" : "");
            tabButton.setTooltip(Tooltip.create(Component.literal(tab.label + countStr)));
            this.addRenderableWidget(tabButton);

            tabX += tabWidth + 2;
        }

        if (tabs.isEmpty()) return;
        CategoryTab currentTab = tabs.get(activeTabIndex);
        String currentTabId = currentTab.id;

        // 3. Navigation Buttons (< and >)
        if (!"tree".equals(currentTabId) && !"info".equals(currentTabId) && currentTab.recipeCount > 1) {
            // Previous recipe button
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                int cur = tabRecipeIndices.getOrDefault(currentTabId, 0);
                if (cur > 0) {
                    DexSoundHelper.playButtonClick();
                    tabRecipeIndices.put(currentTabId, cur - 1);
                    if ("crafting".equals(currentTabId)) rebuildCategoryButtons();
                }
            }).bounds(guiLeft + 8, guiTop + 42, 18, 16).build());

            // Next recipe button
            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                int cur = tabRecipeIndices.getOrDefault(currentTabId, 0);
                if (cur < currentTab.recipeCount - 1) {
                    DexSoundHelper.playButtonClick();
                    tabRecipeIndices.put(currentTabId, cur + 1);
                    if ("crafting".equals(currentTabId)) rebuildCategoryButtons();
                }
            }).bounds(guiLeft + guiWidth - 26, guiTop + 42, 18, 16).build());
        }

        // 4. Auto-transfer '+' button (for crafting table recipes)
        if ("crafting".equals(currentTabId) && recipesByCategory.containsKey("crafting") && !recipesByCategory.get("crafting").isEmpty()) {
            int cur = tabRecipeIndices.getOrDefault("crafting", 0);
            List<RecipeHolder<?>> craftingList = recipesByCategory.get("crafting");
            if (cur < 0 || cur >= craftingList.size()) cur = 0;
            RecipeHolder<?> holder = craftingList.get(cur);

            RecipeTransferHelper.InventoryCheckResult check = RecipeTransferHelper.checkInventory(holder.value());
            Component buttonText = Component.literal("+").withStyle(check.allPresent() ? ChatFormatting.GREEN : ChatFormatting.RED);

            StringBuilder tooltipSb = new StringBuilder();
            if (check.allPresent()) {
                tooltipSb.append("Transfer Recipe Ingredients\n[Shift-Click] Transfer max amount");
            } else {
                tooltipSb.append("Missing Ingredients:\n");
                for (Map.Entry<Item, Integer> entry : check.missingQuantities().entrySet()) {
                    tooltipSb.append("- ").append(new ItemStack(entry.getKey()).getHoverName().getString())
                            .append(" x").append(entry.getValue()).append("\n");
                }
                tooltipSb.append("[Click] to try transfer anyway");
            }

            Button.Builder plusBuilder = Button.builder(buttonText, b -> {
                DexSoundHelper.playButtonClick();
                transferRecipe();
            }).bounds(guiLeft + guiWidth - 26, guiTop + guiHeight - 24, 18, 16);

            plusBuilder.tooltip(Tooltip.create(Component.literal(tooltipSb.toString().trim())));
            this.addRenderableWidget(plusBuilder.build());
        } else if ("tree".equals(currentTabId)) {
            // Multiplier buttons for tree calculator: [x1] [x4] [x16] [x64]
            int multX = guiLeft + 8;
            int[] mults = {1, 4, 16, 64};
            for (int m : mults) {
                int val = m;
                boolean isCur = treeMultiplier == val;
                this.addRenderableWidget(Button.builder(
                        Component.literal("x" + val).withStyle(isCur ? ChatFormatting.GOLD : ChatFormatting.WHITE),
                        b -> {
                            DexSoundHelper.playButtonClick();
                            this.treeMultiplier = val;
                            this.cachedTree = CraftingTreeCalculator.calculateTree(targetItem, treeMultiplier);
                            rebuildCategoryButtons();
                        }
                ).bounds(multX, guiTop + 42, 28, 16).build());
                multX += 30;
            }
        }
    }

    private void transferRecipe() {
        List<RecipeHolder<?>> craftingList = recipesByCategory.get("crafting");
        if (craftingList == null || craftingList.isEmpty()) return;

        int cur = tabRecipeIndices.getOrDefault("crafting", 0);
        if (cur < 0 || cur >= craftingList.size()) cur = 0;
        RecipeHolder<?> holder = craftingList.get(cur);

        RecipeTransferHelper.executeTransfer(holder, previousScreen);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: Prevent vanilla Screen#renderBackground from invoking renderBlurredBackground(partialTick),
        // which runs Minecraft's post-processing blur shader and blurs the screen/GUI.
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // No-op: Disable the blur shader effect in RecipeViewerScreen to ensure 100% crisp and sharp rendering.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoveredSlotItem = ItemStack.EMPTY;
        hoveredSlotIsMissing = false;

        // 1. Transparent background dimming over the game world (identical to AbstractContainerScreen)
        // Dims the world cleanly without applying any post-processing blur shader, keeping the GUI crystal clear
        this.renderTransparentBackground(graphics);

        // 2. Background Box (Dark Modern Slate Glassmorphism)
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight, 0xF018181E);
        graphics.renderOutline(guiLeft, guiTop, guiWidth, guiHeight, 0xFF4A4A5A);

        // 3. Header Title (Safe Layout - Never Overlaps Back Button)
        String prefix = (mode == Mode.CRAFTING ? "Recipe: " : "Usages: ");
        String fullTitle = prefix + targetItem.getHoverName().getString();

        int titleLeft = !HISTORY.isEmpty() ? (guiLeft + 54) : (guiLeft + 8);
        int titleRight = guiLeft + guiWidth - 8;
        int maxTitleWidth = titleRight - titleLeft - 4;

        // Truncate title if it exceeds available space
        String displayTitle = font.plainSubstrByWidth(fullTitle, maxTitleWidth);
        if (displayTitle.length() < fullTitle.length()) {
            displayTitle = font.plainSubstrByWidth(fullTitle, maxTitleWidth - 10) + "...";
        }
        int titleCenterX = titleLeft + (titleRight - titleLeft) / 2;
        graphics.drawCenteredString(font, displayTitle, titleCenterX, guiTop + 8, 0xFFFFAA00);

        // 3. Render Active Tab Content
        if (tabs.isEmpty()) {
            graphics.drawCenteredString(font, "No recipes found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
        } else {
            CategoryTab activeTab = tabs.get(activeTabIndex);
            int currentIdx = tabRecipeIndices.getOrDefault(activeTab.id, 0);

            switch (activeTab.id) {
                case "brewing" -> renderBrewingView(graphics, currentIdx, mouseX, mouseY);
                case "trading" -> renderTradingView(graphics, currentIdx, mouseX, mouseY);
                case "drops" -> renderMobDropsView(graphics, currentIdx, mouseX, mouseY);
                case "tree" -> renderTreeView(graphics, mouseX, mouseY);
                case "info" -> renderInfoView(graphics, mouseX, mouseY);
                default -> {
                    if (recipesByCategory.containsKey(activeTab.id)) {
                        renderRecipesView(graphics, activeTab.id, currentIdx, mouseX, mouseY);
                    } else if (activeTab.customCategory != null) {
                        renderCustomCategoryView(graphics, activeTab.customCategory, currentIdx, mouseX, mouseY);
                    } else {
                        renderRecipesView(graphics, activeTab.id, currentIdx, mouseX, mouseY);
                    }
                }
            }
        }

        // 4. Render Widgets (Buttons, Tabs) ON TOP of background
        super.render(graphics, mouseX, mouseY, partialTick);

        // 5. Render Tooltips at the very top layer
        if (!hoveredSlotItem.isEmpty()) {
            if (hoveredSlotIsMissing && DEXConfig.get().isHighlightMissingIngredients()) {
                Minecraft mc = Minecraft.getInstance();
                List<Component> tooltipLines = new ArrayList<>(getTooltipFromItem(mc, hoveredSlotItem));
                tooltipLines.add(1, Component.literal("⚠ Missing from Inventory").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
                graphics.renderComponentTooltip(font, tooltipLines, mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, hoveredSlotItem, mouseX, mouseY);
            }
        }
    }

    private void renderRecipesView(GuiGraphics graphics, String categoryId, int currentRecipeIndex, int mouseX, int mouseY) {
        List<RecipeHolder<?>> catRecipes = recipesByCategory.getOrDefault(categoryId, Collections.emptyList());
        if (catRecipes.isEmpty()) {
            graphics.drawCenteredString(font, "No recipes found for this item.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

        if (currentRecipeIndex >= catRecipes.size()) currentRecipeIndex = 0;
        RecipeHolder<?> currentHolder = catRecipes.get(currentRecipeIndex);
        ResourceLocation recipeId = currentHolder.id();

        String pageInfo = "Recipe " + (currentRecipeIndex + 1) + " of " + catRecipes.size();
        graphics.drawCenteredString(font, pageInfo, guiLeft + guiWidth / 2, guiTop + 45, 0xFFFFFFFF);

        String namespace = recipeId.getNamespace();
        ModInfo modInfo = ItemCatalogManager.getInstance().getModInfo(namespace);
        String modName = modInfo != null ? modInfo.getDisplayName() : namespace;
        graphics.drawCenteredString(font, "Mod: " + modName + " (" + recipeId.getPath() + ")",
                guiLeft + guiWidth / 2, guiTop + 58, 0xFF88AAFF);

        renderRecipeBody(graphics, currentHolder, mouseX, mouseY);
    }

    private void renderRecipeBody(GuiGraphics graphics, RecipeHolder<?> holder, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int centerX = guiLeft + guiWidth / 2;
        int centerY = guiTop + 115;

        Recipe<?> recipe = holder.value();
        ItemStack output = recipe.getResultItem(mc.level != null ? mc.level.registryAccess() : null);
        if (output.isEmpty()) {
            output = targetItem.copy();
        }

        List<Ingredient> ingredients = RecipeIngredientHelper.getIngredients(recipe);
        RecipeIngredientHelper.RecipeCategoryInfo catInfo = RecipeIngredientHelper.getCategoryInfo(holder);
        RecipeTransferHelper.InventoryCheckResult check = DEXConfig.get().isHighlightMissingIngredients() ?
                RecipeTransferHelper.checkInventory(recipe) : null;

        if (recipe instanceof CraftingRecipe) {
            // Crafting Table 3x3 Grid
            int gridLeft = centerX - 76;
            int gridTop = centerY - 28;

            int width = 3;
            int height = 3;
            if (recipe instanceof ShapedRecipe shaped) {
                width = shaped.getWidth();
                height = shaped.getHeight();
            }

            int ingIdx = 0;
            for (int r = 0; r < height; r++) {
                for (int c = 0; c < width; c++) {
                    int slotX = gridLeft + (c * 18);
                    int slotY = gridTop + (r * 18);
                    drawSlot(graphics, slotX, slotY);

                    if (ingIdx < ingredients.size()) {
                        Ingredient ing = ingredients.get(ingIdx);
                        boolean isMissing = check != null && check.missingSlotIndices().contains(ingIdx);
                        renderIngredient(graphics, ing, slotX + 1, slotY + 1, mouseX, mouseY, isMissing);
                        if (isMissing) {
                            graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0x55FF3333);
                        }
                        ingIdx++;
                    }
                }
            }

            graphics.drawString(font, "➔", centerX - 4, centerY - 6, 0xFFFFFFFF);

            int outX = centerX + 24;
            int outY = centerY - 10;
            drawSlot(graphics, outX, outY);
            if (!output.isEmpty()) {
                graphics.renderItem(output, outX + 1, outY + 1);
                graphics.renderItemDecorations(font, output, outX + 1, outY + 1);
                checkSlotHover(output, outX + 1, outY + 1, mouseX, mouseY);
            }

        } else if (recipe instanceof AbstractCookingRecipe cooking) {
            // Cooking (Smelting / Blasting / Smoking)
            int inX = centerX - 54;
            int inY = centerY - 10;
            drawSlot(graphics, inX, inY);
            if (!ingredients.isEmpty()) {
                boolean isMissing = check != null && check.missingSlotIndices().contains(0);
                renderIngredient(graphics, ingredients.get(0), inX + 1, inY + 1, mouseX, mouseY, isMissing);
                if (isMissing) {
                    graphics.fill(inX + 1, inY + 1, inX + 17, inY + 17, 0x55FF3333);
                }
            }

            graphics.drawString(font, "♨", centerX - 20, centerY - 6, 0xFFFF5555);
            graphics.drawString(font, "➔", centerX - 4, centerY - 6, 0xFFFFFFFF);

            int outX = centerX + 24;
            int outY = centerY - 10;
            drawSlot(graphics, outX, outY);
            if (!output.isEmpty()) {
                graphics.renderItem(output, outX + 1, outY + 1);
                graphics.renderItemDecorations(font, output, outX + 1, outY + 1);
                checkSlotHover(output, outX + 1, outY + 1, mouseX, mouseY);
            }

            String info = (cooking.getCookingTime() / 20) + "s (" + cooking.getExperience() + " XP)";
            graphics.drawCenteredString(font, info, centerX, centerY + 24, 0xFFAAAAAA);

        } else if (catInfo.kind() == RecipeIngredientHelper.WorkstationKind.SMITHING_TABLE) {
            // Smithing Table: Template + Base + Addition -> Result
            int tX = centerX - 70;
            int bX = centerX - 48;
            int aX = centerX - 26;

            drawSlot(graphics, tX, centerY - 10);
            drawSlot(graphics, bX, centerY - 10);
            drawSlot(graphics, aX, centerY - 10);

            if (ingredients.size() >= 1) {
                boolean isMissing = check != null && check.missingSlotIndices().contains(0);
                renderIngredient(graphics, ingredients.get(0), tX + 1, centerY - 9, mouseX, mouseY, isMissing);
                if (isMissing) graphics.fill(tX + 1, centerY - 9, tX + 17, centerY + 7, 0x55FF3333);
            }
            if (ingredients.size() >= 2) {
                boolean isMissing = check != null && check.missingSlotIndices().contains(1);
                renderIngredient(graphics, ingredients.get(1), bX + 1, centerY - 9, mouseX, mouseY, isMissing);
                if (isMissing) graphics.fill(bX + 1, centerY - 9, bX + 17, centerY + 7, 0x55FF3333);
            }
            if (ingredients.size() >= 3) {
                boolean isMissing = check != null && check.missingSlotIndices().contains(2);
                renderIngredient(graphics, ingredients.get(2), aX + 1, centerY - 9, mouseX, mouseY, isMissing);
                if (isMissing) graphics.fill(aX + 1, centerY - 9, aX + 17, centerY + 7, 0x55FF3333);
            }

            graphics.drawString(font, "🔨➔", centerX - 4, centerY - 6, 0xFFFFAA00);

            int outX = centerX + 34;
            drawSlot(graphics, outX, centerY - 10);
            if (!output.isEmpty()) {
                graphics.renderItem(output, outX + 1, centerY - 9);
                graphics.renderItemDecorations(font, output, outX + 1, centerY - 9);
                checkSlotHover(output, outX + 1, centerY - 9, mouseX, mouseY);
            }
            graphics.drawCenteredString(font, "Smithing Table Upgrade", centerX, centerY + 24, 0xFFAAAAAA);

        } else if (catInfo.kind() == RecipeIngredientHelper.WorkstationKind.FUSION_ANVIL || ingredients.size() == 2) {
            // 2-Input Fusion / Anvil
            int bX = centerX - 58;
            int aX = centerX - 28;

            drawSlot(graphics, bX, centerY - 10);
            drawSlot(graphics, aX, centerY - 10);

            if (!ingredients.isEmpty()) {
                boolean isMissing = check != null && check.missingSlotIndices().contains(0);
                renderIngredient(graphics, ingredients.get(0), bX + 1, centerY - 9, mouseX, mouseY, isMissing);
                if (isMissing) graphics.fill(bX + 1, centerY - 9, bX + 17, centerY + 7, 0x55FF3333);
            }
            graphics.drawString(font, "+", centerX - 37, centerY - 5, 0xFFFFAA00);
            if (ingredients.size() >= 2) {
                boolean isMissing = check != null && check.missingSlotIndices().contains(1);
                renderIngredient(graphics, ingredients.get(1), aX + 1, centerY - 9, mouseX, mouseY, isMissing);
                if (isMissing) graphics.fill(aX + 1, centerY - 9, aX + 17, centerY + 7, 0x55FF3333);
            }

            graphics.drawString(font, "➔", centerX - 8, centerY - 6, 0xFFFFFFFF);

            int outX = centerX + 26;
            drawSlot(graphics, outX, centerY - 10);
            if (!output.isEmpty()) {
                graphics.renderItem(output, outX + 1, centerY - 9);
                graphics.renderItemDecorations(font, output, outX + 1, centerY - 9);
                checkSlotHover(output, outX + 1, centerY - 9, mouseX, mouseY);
            }
            String title = catInfo.title().getString();
            graphics.drawCenteredString(font, title, centerX, centerY + 24, 0xFFAAAAAA);

        } else if (catInfo.kind() == RecipeIngredientHelper.WorkstationKind.STONECUTTER || recipe instanceof StonecutterRecipe) {
            // Stonecutter
            int inX = centerX - 50;
            int inY = centerY - 10;
            drawSlot(graphics, inX, inY);
            if (!ingredients.isEmpty()) {
                boolean isMissing = check != null && check.missingSlotIndices().contains(0);
                renderIngredient(graphics, ingredients.get(0), inX + 1, inY + 1, mouseX, mouseY, isMissing);
                if (isMissing) graphics.fill(inX + 1, inY + 1, inX + 17, inY + 17, 0x55FF3333);
            }

            graphics.drawString(font, "🪚➔", centerX - 12, centerY - 6, 0xFFFFFFFF);

            int outX = centerX + 24;
            int outY = centerY - 10;
            drawSlot(graphics, outX, outY);
            if (!output.isEmpty()) {
                graphics.renderItem(output, outX + 1, outY + 1);
                graphics.renderItemDecorations(font, output, outX + 1, outY + 1);
                checkSlotHover(output, outX + 1, outY + 1, mouseX, mouseY);
            }
            graphics.drawCenteredString(font, "Stonecutter", centerX, centerY + 24, 0xFFAAAAAA);

        } else {
            // Generic Multi-Input Fallback
            int ingCount = ingredients.size();
            if (ingCount <= 1) {
                int inX = centerX - 50;
                int inY = centerY - 10;
                drawSlot(graphics, inX, inY);
                if (!ingredients.isEmpty()) {
                    boolean isMissing = check != null && check.missingSlotIndices().contains(0);
                    renderIngredient(graphics, ingredients.get(0), inX + 1, inY + 1, mouseX, mouseY, isMissing);
                    if (isMissing) graphics.fill(inX + 1, inY + 1, inX + 17, inY + 17, 0x55FF3333);
                }

                graphics.drawString(font, "➔", centerX - 4, centerY - 6, 0xFFFFFFFF);

                int outX = centerX + 24;
                int outY = centerY - 10;
                drawSlot(graphics, outX, outY);
                if (!output.isEmpty()) {
                    graphics.renderItem(output, outX + 1, outY + 1);
                    graphics.renderItemDecorations(font, output, outX + 1, outY + 1);
                    checkSlotHover(output, outX + 1, outY + 1, mouseX, mouseY);
                }
            } else {
                int cols = Math.min(3, ingCount);
                int rows = (ingCount + cols - 1) / cols;
                int startX = centerX - (cols * 19) - 10;
                int startY = centerY - (rows * 10);

                for (int i = 0; i < ingCount; i++) {
                    int c = i % cols;
                    int r = i / cols;
                    int sx = startX + (c * 18);
                    int sy = startY + (r * 18);
                    drawSlot(graphics, sx, sy);
                    boolean isMissing = check != null && check.missingSlotIndices().contains(i);
                    renderIngredient(graphics, ingredients.get(i), sx + 1, sy + 1, mouseX, mouseY, isMissing);
                    if (isMissing) graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x55FF3333);
                }

                graphics.drawString(font, "➔", centerX - 4, centerY - 6, 0xFFFFFFFF);

                int outX = centerX + 24;
                int outY = centerY - 10;
                drawSlot(graphics, outX, outY);
                if (!output.isEmpty()) {
                    graphics.renderItem(output, outX + 1, outY + 1);
                    graphics.renderItemDecorations(font, output, outX + 1, outY + 1);
                    checkSlotHover(output, outX + 1, outY + 1, mouseX, mouseY);
                }
            }
        }

        // Dedicated Workstation / Catalyst Badge at bottom-left
        ItemStack wsIcon = catInfo.icon();
        if (!wsIcon.isEmpty()) {
            int badgeX = guiLeft + 12;
            int badgeY = guiTop + guiHeight - 24;
            drawSlot(graphics, badgeX, badgeY);
            graphics.renderItem(wsIcon, badgeX + 1, badgeY + 1);
            graphics.renderItemDecorations(font, wsIcon, badgeX + 1, badgeY + 1);
            checkSlotHover(wsIcon, badgeX + 1, badgeY + 1, mouseX, mouseY);

            String stationName = (catInfo != null && catInfo.title() != null) ? catInfo.title().getString() : wsIcon.getHoverName().getString();
            String displayStation = font.plainSubstrByWidth("Station: " + stationName, guiWidth - 76);
            graphics.drawString(font, displayStation, badgeX + 22, badgeY + 5, 0xFFAAAAAA);
        }
    }

    private void renderBrewingView(GuiGraphics graphics, int currentBrewingIndex, int mouseX, int mouseY) {
        if (brewingRecipes.isEmpty()) {
            graphics.drawCenteredString(font, "No brewing recipes found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

        if (currentBrewingIndex >= brewingRecipes.size()) currentBrewingIndex = 0;
        BrewingRecipeEntry entry = brewingRecipes.get(currentBrewingIndex);
        int centerX = guiLeft + guiWidth / 2;
        int centerY = guiTop + 115;

        String pageInfo = "Brewing " + (currentBrewingIndex + 1) + " of " + brewingRecipes.size();
        graphics.drawCenteredString(font, pageInfo, guiLeft + guiWidth / 2, guiTop + 45, 0xFFFFFFFF);

        // Input Potion (Left)
        int inX = centerX - 54;
        int inY = centerY - 10;
        drawSlot(graphics, inX, inY);
        graphics.renderItem(entry.getInput(), inX + 1, inY + 1);
        graphics.renderItemDecorations(font, entry.getInput(), inX + 1, inY + 1);
        checkSlotHover(entry.getInput(), inX + 1, inY + 1, mouseX, mouseY);

        // Brewing Ingredient (Top-Center)
        int ingX = centerX - 8;
        int ingY = centerY - 28;
        drawSlot(graphics, ingX, ingY);
        graphics.renderItem(entry.getIngredient(), ingX + 1, ingY + 1);
        graphics.renderItemDecorations(font, entry.getIngredient(), ingX + 1, ingY + 1);
        checkSlotHover(entry.getIngredient(), ingX + 1, ingY + 1, mouseX, mouseY);

        // Brewing Stand Symbol
        graphics.drawString(font, "⚗➔", centerX - 6, centerY - 6, 0xFFFFAA00);

        // Output Potion (Right)
        int outX = centerX + 34;
        int outY = centerY - 10;
        drawSlot(graphics, outX, outY);
        graphics.renderItem(entry.getOutput(), outX + 1, outY + 1);
        graphics.renderItemDecorations(font, entry.getOutput(), outX + 1, outY + 1);
        checkSlotHover(entry.getOutput(), outX + 1, outY + 1, mouseX, mouseY);

        graphics.drawCenteredString(font, "Brewing Stand Recipe", centerX, centerY + 24, 0xFFAAAAAA);

        // Dedicated Workstation / Catalyst Badge at bottom-left
        int badgeX = guiLeft + 12;
        int badgeY = guiTop + guiHeight - 24;
        ItemStack wsIcon = new ItemStack(Items.BREWING_STAND);
        drawSlot(graphics, badgeX, badgeY);
        graphics.renderItem(wsIcon, badgeX + 1, badgeY + 1);
        graphics.renderItemDecorations(font, wsIcon, badgeX + 1, badgeY + 1);
        checkSlotHover(wsIcon, badgeX + 1, badgeY + 1, mouseX, mouseY);
        graphics.drawString(font, "Station: Brewing Stand", badgeX + 22, badgeY + 5, 0xFFAAAAAA);
    }

    public static ItemStack getProfessionWorkstation(String profession) {
        if (profession == null) return new ItemStack(Items.EMERALD);
        String p = profession.toLowerCase(Locale.ROOT);
        return switch (p) {
            case "armorer" -> new ItemStack(Blocks.BLAST_FURNACE);
            case "butcher" -> new ItemStack(Blocks.SMOKER);
            case "cartographer" -> new ItemStack(Blocks.CARTOGRAPHY_TABLE);
            case "cleric" -> new ItemStack(Blocks.BREWING_STAND);
            case "farmer" -> new ItemStack(Blocks.COMPOSTER);
            case "fisherman" -> new ItemStack(Blocks.BARREL);
            case "fletcher" -> new ItemStack(Blocks.FLETCHING_TABLE);
            case "leatherworker" -> new ItemStack(Blocks.CAULDRON);
            case "librarian" -> new ItemStack(Blocks.LECTERN);
            case "mason" -> new ItemStack(Blocks.STONECUTTER);
            case "shepherd" -> new ItemStack(Blocks.LOOM);
            case "toolsmith" -> new ItemStack(Blocks.SMITHING_TABLE);
            case "weaponsmith" -> new ItemStack(Blocks.GRINDSTONE);
            default -> new ItemStack(Items.EMERALD);
        };
    }

    private void renderTradingView(GuiGraphics graphics, int currentTradeIndex, int mouseX, int mouseY) {
        if (villagerTrades.isEmpty()) {
            graphics.drawCenteredString(font, "No villager trades found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

        if (currentTradeIndex >= villagerTrades.size()) currentTradeIndex = 0;
        VillagerTradeEntry trade = villagerTrades.get(currentTradeIndex);
        int centerX = guiLeft + guiWidth / 2;
        int centerY = guiTop + 115;

        String pageInfo = "Trade " + (currentTradeIndex + 1) + " of " + villagerTrades.size();
        graphics.drawCenteredString(font, pageInfo, guiLeft + guiWidth / 2, guiTop + 45, 0xFFFFFFFF);

        // Villager profession & level info banner
        String profTitle = formatTitle(trade.getProfession()) + " • " + trade.getLevelName() + " (Lvl " + trade.getLevel() + ")";
        graphics.drawCenteredString(font, profTitle, guiLeft + guiWidth / 2, guiTop + 58, 0xFF55FF55);

        // 1. Workstation block slot on the left
        int wsX = centerX - 82;
        int wsY = centerY - 10;
        ItemStack wsIcon = getProfessionWorkstation(trade.getProfession());
        drawSlot(graphics, wsX, wsY);
        graphics.renderItem(wsIcon, wsX + 1, wsY + 1);
        graphics.renderItemDecorations(font, wsIcon, wsX + 1, wsY + 1);
        checkSlotHover(wsIcon, wsX + 1, wsY + 1, mouseX, mouseY);
        graphics.drawCenteredString(font, "Station", wsX + 9, wsY + 20, 0xFFAAAAAA);

        // Separator
        graphics.drawString(font, "│", centerX - 58, centerY - 6, 0xFF555566);

        // 2. Cost A
        int costAX = centerX - 48;
        drawSlot(graphics, costAX, centerY - 10);
        graphics.renderItem(trade.getCostA(), costAX + 1, centerY - 9);
        graphics.renderItemDecorations(font, trade.getCostA(), costAX + 1, centerY - 9);
        checkSlotHover(trade.getCostA(), costAX + 1, centerY - 9, mouseX, mouseY);

        // Cost B (Optional)
        int costBX = centerX - 26;
        if (!trade.getCostB().isEmpty()) {
            drawSlot(graphics, costBX, centerY - 10);
            graphics.renderItem(trade.getCostB(), costBX + 1, centerY - 9);
            graphics.renderItemDecorations(font, trade.getCostB(), costBX + 1, centerY - 9);
            checkSlotHover(trade.getCostB(), costBX + 1, centerY - 9, mouseX, mouseY);
        }

        graphics.drawString(font, "➔", centerX - 4, centerY - 6, 0xFFFFAA00);

        // 3. Result Item
        int outX = centerX + 18;
        drawSlot(graphics, outX, centerY - 10);
        graphics.renderItem(trade.getResult(), outX + 1, centerY - 9);
        graphics.renderItemDecorations(font, trade.getResult(), outX + 1, centerY - 9);
        checkSlotHover(trade.getResult(), outX + 1, centerY - 9, mouseX, mouseY);
        graphics.drawCenteredString(font, "Trade Result", outX + 9, centerY + 10, 0xFFAAAAAA);

        graphics.drawCenteredString(font, "Villager Profession Trade Offer", centerX, centerY + 28, 0xFF888899);
    }

    private void renderMobDropsView(GuiGraphics graphics, int currentDropIndex, int mouseX, int mouseY) {
        if (mobDrops.isEmpty()) {
            graphics.drawCenteredString(font, "No mob drops found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

        if (currentDropIndex >= mobDrops.size()) currentDropIndex = 0;
        MobDropEntry drop = mobDrops.get(currentDropIndex);
        int centerX = guiLeft + guiWidth / 2;
        int centerY = guiTop + 115;

        String pageInfo = "Mob Drop " + (currentDropIndex + 1) + " of " + mobDrops.size();
        graphics.drawCenteredString(font, pageInfo, guiLeft + guiWidth / 2, guiTop + 45, 0xFFFFFFFF);

        // Entity name banner
        String entityName = drop.getMobName();
        graphics.drawCenteredString(font, "Source Entity: " + entityName,
                guiLeft + guiWidth / 2, guiTop + 58, 0xFFFFAA00);

        // 1. Mob Source Slot (Spawn Egg or Mob Head)
        int mobX = centerX - 56;
        int mobY = centerY - 10;
        drawSlot(graphics, mobX, mobY);
        ItemStack mobIcon = drop.getRepresentativeIcon();
        if (!mobIcon.isEmpty()) {
            graphics.renderItem(mobIcon, mobX + 1, mobY + 1);
            graphics.renderItemDecorations(font, mobIcon, mobX + 1, mobY + 1);
            checkSlotHover(mobIcon, mobX + 1, mobY + 1, mouseX, mouseY);
        }
        graphics.drawCenteredString(font, "Mob", mobX + 9, mobY + 20, 0xFFAAAAAA);

        // 2. Center Trajectory / Arrow with Drop Chance
        graphics.drawString(font, "──🗡 Drop──➔", centerX - 26, centerY - 6, 0xFFFF5555);
        String chanceInfo = "Chance: " + drop.getDropChance();
        graphics.drawCenteredString(font, chanceInfo, centerX + 4, centerY + 8, 0xFFFFAA00);

        // 3. Drop Result Slot
        int outX = centerX + 38;
        int outY = centerY - 10;
        drawSlot(graphics, outX, outY);
        graphics.renderItem(drop.getDropItem(), outX + 1, outY + 1);
        graphics.renderItemDecorations(font, drop.getDropItem(), outX + 1, outY + 1);
        checkSlotHover(drop.getDropItem(), outX + 1, outY + 1, mouseX, mouseY);
        graphics.drawCenteredString(font, "Drop Item", outX + 9, outY + 20, 0xFFAAAAAA);

        graphics.drawCenteredString(font, "Entity Drop Loot", centerX, centerY + 28, 0xFF888899);
    }

    @SuppressWarnings("unchecked")
    private <T> void renderCustomCategoryView(GuiGraphics graphics, IDexRecipeCategory<T> category, int currentIdx, int mouseX, int mouseY) {
        List<DexRegistriesImpl.CustomRecipeEntry> entries = customCategories.get(category);
        if (entries == null || entries.isEmpty()) {
            graphics.drawCenteredString(font, "No custom machine recipes found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

        if (currentIdx >= entries.size()) currentIdx = 0;
        DexRegistriesImpl.CustomRecipeEntry entry = entries.get(currentIdx);
        T recipe = (T) entry.recipe();

        String pageInfo = "Recipe " + (currentIdx + 1) + " of " + entries.size();
        graphics.drawCenteredString(font, pageInfo, guiLeft + guiWidth / 2, guiTop + 45, 0xFFFFFFFF);

        String catTitle = category.getTitle() != null ? category.getTitle().getString() : category.getId().getPath();
        graphics.drawCenteredString(font, "Machine: " + catTitle, guiLeft + guiWidth / 2, guiTop + 58, 0xFFFFAA00);

        int recipeX = guiLeft + 20;
        int recipeY = guiTop + 72;
        int recipeWidth = guiWidth - 40;
        int recipeHeight = 85;

        // Custom Category Background & Draw
        graphics.fill(recipeX, recipeY, recipeX + recipeWidth, recipeY + recipeHeight, 0xFF22222A);
        graphics.renderOutline(recipeX, recipeY, recipeWidth, recipeHeight, 0xFF444455);

        try {
            category.draw(recipe, graphics, mouseX, mouseY);
        } catch (Throwable t) {
            // Draw safely if third-party draw throws
        }

        // Render Dynamic Captured Slots
        List<DexRecipeSlot> slots = category.getSlots(recipe);
        if (slots != null && !slots.isEmpty()) {
            long time = System.currentTimeMillis() / 1000;
            for (DexRecipeSlot slot : slots) {
                int slotRenderX = recipeX + slot.x();
                int slotRenderY = recipeY + slot.y();
                drawSlot(graphics, slotRenderX, slotRenderY);

                if (!slot.items().isEmpty()) {
                    int itemIdx = (int) (time % slot.items().size());
                    ItemStack stack = slot.items().get(itemIdx);
                    if (!stack.isEmpty()) {
                        graphics.renderItem(stack, slotRenderX + 1, slotRenderY + 1);
                        graphics.renderItemDecorations(font, stack, slotRenderX + 1, slotRenderY + 1);
                        checkSlotHover(stack, slotRenderX + 1, slotRenderY + 1, mouseX, mouseY);
                    }
                }
            }
        }

        // Dedicated Workstation / Catalyst Badge for Custom Machine at bottom-left
        ItemStack wsIcon = category.getIcon();
        if (wsIcon != null && !wsIcon.isEmpty()) {
            int badgeX = guiLeft + 12;
            int badgeY = guiTop + guiHeight - 24;
            drawSlot(graphics, badgeX, badgeY);
            graphics.renderItem(wsIcon, badgeX + 1, badgeY + 1);
            graphics.renderItemDecorations(font, wsIcon, badgeX + 1, badgeY + 1);
            checkSlotHover(wsIcon, badgeX + 1, badgeY + 1, mouseX, mouseY);

            String stationName = wsIcon.getHoverName().getString();
            String displayStation = font.plainSubstrByWidth("Machine: " + stationName, guiWidth - 76);
            graphics.drawString(font, displayStation, badgeX + 22, badgeY + 5, 0xFFAAAAAA);
        }
    }

    private void renderTreeView(GuiGraphics graphics, int mouseX, int mouseY) {
        if (cachedTree == null) {
            graphics.drawCenteredString(font, "No crafting tree available.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

        int startX = guiLeft + 12;
        int startY = guiTop + 64;

        graphics.drawString(font, "Target: " + targetItem.getHoverName().getString() + " (x" + treeMultiplier + ")",
                startX, startY, 0xFFFFAA00);

        int curY = startY + 16;
        curY = renderTreeNode(graphics, cachedTree, startX, curY, 0, mouseX, mouseY);

        // Render Summary of Base Materials at bottom
        curY += 6;
        graphics.drawString(font, "Total Raw Materials Needed:", startX, curY, 0xFF55FF55);
        curY += 12;

        int matX = startX;
        for (Map.Entry<Item, Integer> entry : cachedTree.getRawMaterialsSummary().entrySet()) {
            ItemStack baseStack = new ItemStack(entry.getKey(), entry.getValue());
            drawSlot(graphics, matX, curY);
            graphics.renderItem(baseStack, matX + 1, curY + 1);
            graphics.renderItemDecorations(font, baseStack, matX + 1, curY + 1);
            checkSlotHover(baseStack, matX + 1, curY + 1, mouseX, mouseY);

            matX += 20;
            if (matX > guiLeft + guiWidth - 28) {
                matX = startX;
                curY += 20;
            }
        }
    }

    private int renderTreeNode(GuiGraphics graphics, CraftingTreeNode node, int x, int y, int depth, int mouseX, int mouseY) {
        if (depth > 4 || y > guiTop + guiHeight - 50) return y;

        int indent = depth * 14;
        String prefix = depth == 0 ? "● " : "└─ ";
        ItemStack stack = node.getItem();

        graphics.drawString(font, prefix + stack.getHoverName().getString() + " x" + node.getCount(),
                x + indent + 18, y + 4, node.isBaseMaterial() ? 0xFF55FF55 : 0xFFFFFFFF);

        drawSlot(graphics, x + indent, y);
        graphics.renderItem(stack, x + indent + 1, y + 1);
        checkSlotHover(stack, x + indent + 1, y + 1, mouseX, mouseY);

        y += 20;
        for (CraftingTreeNode child : node.getChildren()) {
            y = renderTreeNode(graphics, child, x, y, depth + 1, mouseX, mouseY);
        }
        return y;
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF373742);
        graphics.renderOutline(x, y, 18, 18, 0xFF5C5C70);
    }

    private void renderIngredient(GuiGraphics graphics, Ingredient ingredient, int x, int y, int mouseX, int mouseY) {
        renderIngredient(graphics, ingredient, x, y, mouseX, mouseY, false);
    }

    private void renderIngredient(GuiGraphics graphics, Ingredient ingredient, int x, int y, int mouseX, int mouseY, boolean isMissing) {
        if (ingredient == null || ingredient.isEmpty()) return;
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return;

        int index = (int) ((System.currentTimeMillis() / 1000) % items.length);
        ItemStack stack = items[index];

        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        checkSlotHover(stack, x, y, mouseX, mouseY, isMissing);
    }

    private void checkSlotHover(ItemStack stack, int x, int y, int mouseX, int mouseY) {
        checkSlotHover(stack, x, y, mouseX, mouseY, false);
    }

    private void checkSlotHover(ItemStack stack, int x, int y, int mouseX, int mouseY, boolean isMissing) {
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            this.hoveredSlotItem = stack;
            this.hoveredSlotIsMissing = isMissing;
        }
    }

    private void renderInfoView(GuiGraphics graphics, int mouseX, int mouseY) {
        int startX = guiLeft + 16;
        int startY = guiTop + 45;

        // 1. Header with icon and item title
        drawSlot(graphics, startX, startY);
        graphics.renderItem(targetItem, startX + 1, startY + 1);
        graphics.renderItemDecorations(font, targetItem, startX + 1, startY + 1);
        checkSlotHover(targetItem, startX + 1, startY + 1, mouseX, mouseY, false);

        graphics.drawString(font, targetItem.getHoverName().getString(), startX + 24, startY + 5, 0xFFFFAA00);

        // 2. Info container box
        int boxX = startX;
        int boxY = startY + 24;
        int boxWidth = guiWidth - 32;
        int boxHeight = guiHeight - 78;
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x40101018);
        graphics.renderOutline(boxX, boxY, boxWidth, boxHeight, 0xFF3E3E50);

        // 3. Information lines
        List<Component> descriptions = ItemInfoRegistry.getInstance().getInfo(targetItem.getItem());
        int textY = boxY + 8;
        int textX = boxX + 8;
        int maxTextWidth = boxWidth - 16;

        if (descriptions.isEmpty()) {
            graphics.drawString(font, "No additional guide available for this item.", textX, textY, 0xFF888888);
            return;
        }

        for (Component desc : descriptions) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(desc, maxTextWidth);
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                if (textY + 10 > boxY + boxHeight - 6) {
                    graphics.drawString(font, "...", textX, textY, 0xFF888888);
                    break;
                }
                graphics.drawString(font, line, textX, textY, 0xFFE0E0E0);
                textY += 11;
            }
            textY += 4;
            if (textY + 10 > boxY + boxHeight - 6) break;
        }
    }

    private static String formatTitle(String raw) {
        if (raw == null || raw.isEmpty()) return "Recipe";
        String cleaned = raw.replace('_', ' ').replace('/', ' ');
        String[] words = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!hoveredSlotItem.isEmpty()) {
            if (button == 0) {
                DexSoundHelper.playButtonClick();
                openRecipes(hoveredSlotItem);
                return true;
            } else if (button == 1) {
                DexSoundHelper.playButtonClick();
                openUsages(hoveredSlotItem);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC or Inventory key: return or close
        if (keyCode == 256 || keyCode == Minecraft.getInstance().options.keyInventory.getKey().getValue()) {
            Minecraft.getInstance().setScreen(previousScreen);
            return true;
        }

        // Backspace (keyCode 259): return to previous recipe in history
        if (keyCode == 259) {
            DexSoundHelper.playButtonClick();
            goBack();
            return true;
        }

        // Hovered Slot Shortcuts: R (Recipes), U (Usages), A (Pin to Bookmarks)
        if (!hoveredSlotItem.isEmpty()) {
            if (keyCode == 82) { // R
                DexSoundHelper.playButtonClick();
                openRecipes(hoveredSlotItem);
                return true;
            } else if (keyCode == 85) { // U
                DexSoundHelper.playButtonClick();
                openUsages(hoveredSlotItem);
                return true;
            } else if (keyCode == 65) { // A
                boolean added = BookmarkManager.getInstance().toggleBookmark(hoveredSlotItem);
                DexSoundHelper.playButtonClick(1.2F);
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("DEX: " + (added ? "Pinned " : "Unpinned ") + hoveredSlotItem.getHoverName().getString() + " to Bookmarks!")
                                    .withStyle(added ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                            true
                    );
                }
                return true;
            }
        }

        // Left Arrow (263) and Right Arrow (262): page through recipes of the active tab
        if (!tabs.isEmpty()) {
            CategoryTab activeTab = tabs.get(activeTabIndex);
            if (activeTab.recipeCount > 1) {
                int cur = tabRecipeIndices.getOrDefault(activeTab.id, 0);
                if (keyCode == 263 && cur > 0) { // Left arrow
                    DexSoundHelper.playButtonClick();
                    tabRecipeIndices.put(activeTab.id, cur - 1);
                    if ("crafting".equals(activeTab.id)) rebuildCategoryButtons();
                    return true;
                } else if (keyCode == 262 && cur < activeTab.recipeCount - 1) { // Right arrow
                    DexSoundHelper.playButtonClick();
                    tabRecipeIndices.put(activeTab.id, cur + 1);
                    if ("crafting".equals(activeTab.id)) rebuildCategoryButtons();
                    return true;
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 1. Mouse wheel over category tab bar (guiTop + 18 to guiTop + 42)
        if (mouseY >= guiTop + 18 && mouseY <= guiTop + 42 && mouseX >= guiLeft && mouseX <= guiLeft + guiWidth) {
            int visibleCount = tabs.size() > 9 ? 8 : 9;
            if (tabs.size() > visibleCount) {
                if (scrollY < 0 && categoryTabOffset + visibleCount < tabs.size()) {
                    categoryTabOffset++;
                    rebuildCategoryButtons();
                    return true;
                } else if (scrollY > 0 && categoryTabOffset > 0) {
                    categoryTabOffset--;
                    rebuildCategoryButtons();
                    return true;
                }
            }
        }

        // 2. Mouse wheel over main recipe content area (guiTop + 42 to guiTop + guiHeight)
        if (mouseX >= guiLeft && mouseX <= guiLeft + guiWidth && mouseY >= guiTop + 42 && mouseY <= guiTop + guiHeight) {
            if (!tabs.isEmpty()) {
                CategoryTab currentTab = tabs.get(activeTabIndex);
                if (!"tree".equals(currentTab.id) && !"info".equals(currentTab.id) && currentTab.recipeCount > 1) {
                    int cur = tabRecipeIndices.getOrDefault(currentTab.id, 0);
                    if (scrollY < 0 && cur < currentTab.recipeCount - 1) { // Scroll down = next recipe
                        tabRecipeIndices.put(currentTab.id, cur + 1);
                        if ("crafting".equals(currentTab.id)) rebuildCategoryButtons();
                        return true;
                    } else if (scrollY > 0 && cur > 0) { // Scroll up = previous recipe
                        tabRecipeIndices.put(currentTab.id, cur - 1);
                        if ("crafting".equals(currentTab.id)) rebuildCategoryButtons();
                        return true;
                    }
                }
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
