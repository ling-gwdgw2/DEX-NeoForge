package com.dex.client.gui.recipe;

import com.dex.api.DexRecipeSlot;
import com.dex.api.IDexRecipeCategory;
import com.dex.catalog.ItemCatalogManager;
import com.dex.catalog.ModInfo;
import com.dex.client.bookmark.BookmarkManager;
import com.dex.plugin.DexPluginManager;
import com.dex.plugin.DexRegistriesImpl;
import com.dex.recipe.RecipeIndexManager;
import com.dex.recipe.brewing.BrewingIndexManager;
import com.dex.recipe.brewing.BrewingRecipeEntry;
import com.dex.recipe.drops.MobDropEntry;
import com.dex.recipe.drops.MobDropIndexManager;
import com.dex.recipe.trading.VillagerTradeEntry;
import com.dex.recipe.trading.VillagerTradeIndexManager;
import com.dex.recipe.tree.CraftingTreeCalculator;
import com.dex.recipe.tree.CraftingTreeNode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;

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

    private final List<RecipeHolder<?>> recipes;
    private final List<BrewingRecipeEntry> brewingRecipes;
    private final List<VillagerTradeEntry> villagerTrades;
    private final List<MobDropEntry> mobDrops;
    private final Map<IDexRecipeCategory<?>, List<DexRegistriesImpl.CustomRecipeEntry>> customCategories = new LinkedHashMap<>();

    private final List<CategoryTab> tabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private final Map<String, Integer> tabRecipeIndices = new HashMap<>();

    // Tree calculator multiplier
    private int treeMultiplier = 1;
    private CraftingTreeNode cachedTree = null;

    private int guiLeft;
    private int guiTop;
    private final int guiWidth = 260;
    private final int guiHeight = 195;

    private ItemStack hoveredSlotItem = ItemStack.EMPTY;

    public RecipeViewerScreen(Screen previousScreen, ItemStack targetItem, Mode mode) {
        super(Component.literal(mode == Mode.CRAFTING ? "Recipe Viewer" : "Usage Viewer"));
        this.previousScreen = previousScreen;
        this.targetItem = targetItem.copy();
        this.mode = mode;

        if (mode == Mode.CRAFTING) {
            this.recipes = new ArrayList<>(RecipeIndexManager.getInstance().getRecipesFor(targetItem));
            this.brewingRecipes = new ArrayList<>(BrewingIndexManager.getInstance().getRecipesFor(targetItem));
            this.villagerTrades = new ArrayList<>(VillagerTradeIndexManager.getInstance().getTradesSelling(targetItem));
            this.mobDrops = new ArrayList<>(MobDropIndexManager.getInstance().getDropsFor(targetItem));

            List<DexRegistriesImpl.CustomRecipeEntry> customEntries =
                    DexPluginManager.getInstance().getRegistries().getCustomRecipesForOutput(targetItem);
            for (DexRegistriesImpl.CustomRecipeEntry entry : customEntries) {
                this.customCategories.computeIfAbsent(entry.category(), k -> new ArrayList<>()).add(entry);
            }
        } else {
            this.recipes = new ArrayList<>(RecipeIndexManager.getInstance().getUsagesFor(targetItem));
            this.brewingRecipes = new ArrayList<>(BrewingIndexManager.getInstance().getUsagesFor(targetItem));
            this.villagerTrades = new ArrayList<>(VillagerTradeIndexManager.getInstance().getTradesBuying(targetItem));
            this.mobDrops = new ArrayList<>();

            List<DexRegistriesImpl.CustomRecipeEntry> customEntries =
                    DexPluginManager.getInstance().getRegistries().getCustomRecipesForInput(targetItem);
            for (DexRegistriesImpl.CustomRecipeEntry entry : customEntries) {
                this.customCategories.computeIfAbsent(entry.category(), k -> new ArrayList<>()).add(entry);
            }
        }

        buildTabs();
        this.cachedTree = CraftingTreeCalculator.calculateTree(targetItem, treeMultiplier);
    }

    private void buildTabs() {
        tabs.clear();

        // 1. Vanilla / Crafting Tab
        if (!recipes.isEmpty() || (brewingRecipes.isEmpty() && villagerTrades.isEmpty() && mobDrops.isEmpty() && customCategories.isEmpty())) {
            tabs.add(new CategoryTab("recipes", "Crafting", new ItemStack(Items.CRAFTING_TABLE), recipes.size(), null));
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
        if (mode == Mode.CRAFTING && !recipes.isEmpty()) {
            tabs.add(new CategoryTab("tree", "🌳 Tree", new ItemStack(Items.OAK_SAPLING), 1, null));
        }

        // 6. Custom Machine & JEI Plugin Categories
        for (Map.Entry<IDexRecipeCategory<?>, List<DexRegistriesImpl.CustomRecipeEntry>> entry : customCategories.entrySet()) {
            IDexRecipeCategory<?> category = entry.getKey();
            List<DexRegistriesImpl.CustomRecipeEntry> entries = entry.getValue();
            String title = category.getTitle() != null ? category.getTitle().getString() : category.getId().getPath();
            ItemStack icon = category.getIcon() != null && !category.getIcon().isEmpty() ? category.getIcon() : new ItemStack(Items.FURNACE);
            tabs.add(new CategoryTab(category.getId().toString(), title, icon, entries.size(), category));
        }

        if (activeTabIndex >= tabs.size()) {
            activeTabIndex = 0;
        }
    }

    public HistoryEntry saveState() {
        String tabId = !tabs.isEmpty() ? tabs.get(activeTabIndex).id : "recipes";
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

        // 1. Back Button
        if (!HISTORY.isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.literal("⮌ Back"), b -> goBack())
                    .bounds(guiLeft + 6, guiTop + 4, 48, 14).build());
        }

        // 2. Category Tabs at Top
        int tabX = guiLeft + (!HISTORY.isEmpty() ? 58 : 8);
        int tabY = guiTop + 22;
        int tabHeight = 16;

        for (int i = 0; i < tabs.size(); i++) {
            CategoryTab tab = tabs.get(i);
            int tabIndex = i;
            boolean isActive = (i == activeTabIndex);
            int btnWidth = font.width(tab.label) + 10;

            if (tabX + btnWidth > guiLeft + guiWidth - 6) {
                break; // Prevent overflowing screen width
            }

            this.addRenderableWidget(Button.builder(
                    Component.literal(tab.label).withStyle(isActive ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                    b -> {
                        this.activeTabIndex = tabIndex;
                        rebuildCategoryButtons();
                    }
            ).bounds(tabX, tabY, btnWidth, tabHeight).build());

            tabX += btnWidth + 2;
        }

        if (tabs.isEmpty()) return;
        CategoryTab currentTab = tabs.get(activeTabIndex);
        String currentTabId = currentTab.id;
        int currentIndex = tabRecipeIndices.getOrDefault(currentTabId, 0);

        // 3. Navigation Buttons (< and >)
        if (!"tree".equals(currentTabId) && currentTab.recipeCount > 1) {
            // Previous recipe button
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                int cur = tabRecipeIndices.getOrDefault(currentTabId, 0);
                if (cur > 0) {
                    tabRecipeIndices.put(currentTabId, cur - 1);
                }
            }).bounds(guiLeft + 8, guiTop + 42, 18, 16).build());

            // Next recipe button
            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                int cur = tabRecipeIndices.getOrDefault(currentTabId, 0);
                if (cur < currentTab.recipeCount - 1) {
                    tabRecipeIndices.put(currentTabId, cur + 1);
                }
            }).bounds(guiLeft + guiWidth - 26, guiTop + 42, 18, 16).build());
        }

        // 4. Auto-transfer '+' button (only for vanilla crafting table recipes)
        if ("recipes".equals(currentTabId) && !recipes.isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                transferRecipe();
            }).bounds(guiLeft + guiWidth - 26, guiTop + guiHeight - 24, 18, 16).build());
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
        Minecraft mc = Minecraft.getInstance();
        if (recipes.isEmpty() || mc.player == null || mc.getConnection() == null) return;

        int cur = tabRecipeIndices.getOrDefault("recipes", 0);
        if (cur < 0 || cur >= recipes.size()) cur = 0;
        RecipeHolder<?> holder = recipes.get(cur);

        if (mc.player.containerMenu != null) {
            int containerId = mc.player.containerMenu.containerId;
            boolean shift = Screen.hasShiftDown();

            mc.getConnection().send(new ServerboundPlaceRecipePacket(containerId, holder, shift));
            mc.player.displayClientMessage(
                    Component.literal("DEX: Auto-filled recipe to crafting table!").withStyle(ChatFormatting.GREEN),
                    true
            );
            mc.setScreen(previousScreen);
        } else {
            mc.player.displayClientMessage(
                    Component.literal("DEX: Open a Crafting Table to auto-fill!").withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        hoveredSlotItem = ItemStack.EMPTY;

        // Background Box (Dark Modern Slate Glassmorphism)
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight, 0xF018181E);
        graphics.renderOutline(guiLeft, guiTop, guiWidth, guiHeight, 0xFF4A4A5A);

        // Header Title
        String titleStr = (mode == Mode.CRAFTING ? "Crafting: " : "Usages of: ") + targetItem.getHoverName().getString();
        int titleX = guiLeft + guiWidth / 2;
        graphics.drawCenteredString(font, titleStr, titleX, guiTop + 7, 0xFFFFAA00);

        if (tabs.isEmpty()) {
            graphics.drawCenteredString(font, "No recipes found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

        CategoryTab activeTab = tabs.get(activeTabIndex);
        int currentIdx = tabRecipeIndices.getOrDefault(activeTab.id, 0);

        switch (activeTab.id) {
            case "recipes" -> renderRecipesView(graphics, currentIdx, mouseX, mouseY);
            case "brewing" -> renderBrewingView(graphics, currentIdx, mouseX, mouseY);
            case "trading" -> renderTradingView(graphics, currentIdx, mouseX, mouseY);
            case "drops" -> renderMobDropsView(graphics, currentIdx, mouseX, mouseY);
            case "tree" -> renderTreeView(graphics, mouseX, mouseY);
            default -> {
                if (activeTab.customCategory != null) {
                    renderCustomCategoryView(graphics, activeTab.customCategory, currentIdx, mouseX, mouseY);
                }
            }
        }

        // Render Tooltip for hovered slot item
        if (!hoveredSlotItem.isEmpty()) {
            graphics.renderTooltip(font, hoveredSlotItem, mouseX, mouseY);
        }
    }

    private void renderRecipesView(GuiGraphics graphics, int currentRecipeIndex, int mouseX, int mouseY) {
        if (recipes.isEmpty()) {
            graphics.drawCenteredString(font, "No recipes found for this item.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

        if (currentRecipeIndex >= recipes.size()) currentRecipeIndex = 0;
        RecipeHolder<?> currentHolder = recipes.get(currentRecipeIndex);
        Recipe<?> recipe = currentHolder.value();
        ResourceLocation recipeId = currentHolder.id();

        String pageInfo = "Recipe " + (currentRecipeIndex + 1) + " of " + recipes.size();
        graphics.drawCenteredString(font, pageInfo, guiLeft + guiWidth / 2, guiTop + 45, 0xFFFFFFFF);

        String namespace = recipeId.getNamespace();
        ModInfo modInfo = ItemCatalogManager.getInstance().getModInfo(namespace);
        String modName = modInfo != null ? modInfo.getDisplayName() : namespace;
        graphics.drawCenteredString(font, "Mod: " + modName + " (" + recipeId.getPath() + ")",
                guiLeft + guiWidth / 2, guiTop + 58, 0xFF88AAFF);

        renderRecipeBody(graphics, recipe, mouseX, mouseY);
    }

    private void renderRecipeBody(GuiGraphics graphics, Recipe<?> recipe, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int centerX = guiLeft + guiWidth / 2;
        int centerY = guiTop + 115;

        ItemStack output = recipe.getResultItem(mc.level != null ? mc.level.registryAccess() : null);
        NonNullList<Ingredient> ingredients = recipe.getIngredients();

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
                        renderIngredient(graphics, ing, slotX + 1, slotY + 1, mouseX, mouseY);
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
                renderIngredient(graphics, ingredients.get(0), inX + 1, inY + 1, mouseX, mouseY);
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

        } else if (recipe instanceof SmithingRecipe) {
            // Smithing Table: Template + Base + Addition -> Result
            int tX = centerX - 70;
            int bX = centerX - 48;
            int aX = centerX - 26;

            drawSlot(graphics, tX, centerY - 10);
            drawSlot(graphics, bX, centerY - 10);
            drawSlot(graphics, aX, centerY - 10);

            if (ingredients.size() >= 1) renderIngredient(graphics, ingredients.get(0), tX + 1, centerY - 9, mouseX, mouseY);
            if (ingredients.size() >= 2) renderIngredient(graphics, ingredients.get(1), bX + 1, centerY - 9, mouseX, mouseY);
            if (ingredients.size() >= 3) renderIngredient(graphics, ingredients.get(2), aX + 1, centerY - 9, mouseX, mouseY);

            graphics.drawString(font, "🔨➔", centerX - 4, centerY - 6, 0xFFFFAA00);

            int outX = centerX + 34;
            drawSlot(graphics, outX, centerY - 10);
            if (!output.isEmpty()) {
                graphics.renderItem(output, outX + 1, centerY - 9);
                graphics.renderItemDecorations(font, output, outX + 1, centerY - 9);
                checkSlotHover(output, outX + 1, centerY - 9, mouseX, mouseY);
            }
            graphics.drawCenteredString(font, "Smithing Table Upgrade", centerX, centerY + 24, 0xFFAAAAAA);

        } else {
            // Generic Fallback
            int inX = centerX - 50;
            int inY = centerY - 10;
            drawSlot(graphics, inX, inY);
            if (!ingredients.isEmpty()) {
                renderIngredient(graphics, ingredients.get(0), inX + 1, inY + 1, mouseX, mouseY);
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

        // Top: Ingredient Slot
        int ingX = centerX - 9;
        int ingY = centerY - 38;
        drawSlot(graphics, ingX, ingY);
        graphics.renderItem(entry.getIngredient(), ingX + 1, ingY + 1);
        graphics.renderItemDecorations(font, entry.getIngredient(), ingX + 1, ingY + 1);
        checkSlotHover(entry.getIngredient(), ingX + 1, ingY + 1, mouseX, mouseY);

        graphics.drawCenteredString(font, "⚗ ⬇", centerX, centerY - 16, 0xFFE0E0E0);

        // Bottom: 3 Input Bottle Slots
        int b1 = centerX - 36;
        int b2 = centerX - 9;
        int b3 = centerX + 18;
        int botY = centerY;

        drawSlot(graphics, b1, botY);
        drawSlot(graphics, b2, botY);
        drawSlot(graphics, b3, botY);

        graphics.renderItem(entry.getInput(), b1 + 1, botY + 1);
        graphics.renderItem(entry.getInput(), b2 + 1, botY + 1);
        graphics.renderItem(entry.getInput(), b3 + 1, botY + 1);
        checkSlotHover(entry.getInput(), b1 + 1, botY + 1, mouseX, mouseY);

        // Arrow to Result
        graphics.drawString(font, "➔", centerX + 44, centerY + 4, 0xFFFFFFFF);

        // Output Slot
        int outX = centerX + 62;
        drawSlot(graphics, outX, botY);
        graphics.renderItem(entry.getOutput(), outX + 1, botY + 1);
        graphics.renderItemDecorations(font, entry.getOutput(), outX + 1, botY + 1);
        checkSlotHover(entry.getOutput(), outX + 1, botY + 1, mouseX, mouseY);

        graphics.drawCenteredString(font, "Brewing Stand Recipe", centerX, centerY + 30, 0xFFAAAAAA);
    }

    private void renderTradingView(GuiGraphics graphics, int currentTradeIndex, int mouseX, int mouseY) {
        if (villagerTrades.isEmpty()) {
            graphics.drawCenteredString(font, "No trading offers found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

        if (currentTradeIndex >= villagerTrades.size()) currentTradeIndex = 0;
        VillagerTradeEntry trade = villagerTrades.get(currentTradeIndex);
        int centerX = guiLeft + guiWidth / 2;
        int centerY = guiTop + 115;

        String pageInfo = "Trade " + (currentTradeIndex + 1) + " of " + villagerTrades.size();
        graphics.drawCenteredString(font, pageInfo, guiLeft + guiWidth / 2, guiTop + 45, 0xFFFFFFFF);

        String info = trade.getProfession() + " - " + trade.getLevelName();
        graphics.drawCenteredString(font, info, guiLeft + guiWidth / 2, guiTop + 60, 0xFFFFAA00);

        // Cost A Slot
        int c1X = centerX - 58;
        drawSlot(graphics, c1X, centerY - 10);
        graphics.renderItem(trade.getCostA(), c1X + 1, centerY - 9);
        graphics.renderItemDecorations(font, trade.getCostA(), c1X + 1, centerY - 9);
        checkSlotHover(trade.getCostA(), c1X + 1, centerY - 9, mouseX, mouseY);

        // Cost B Slot (optional)
        int c2X = centerX - 34;
        if (!trade.getCostB().isEmpty()) {
            drawSlot(graphics, c2X, centerY - 10);
            graphics.renderItem(trade.getCostB(), c2X + 1, centerY - 9);
            graphics.renderItemDecorations(font, trade.getCostB(), c2X + 1, centerY - 9);
            checkSlotHover(trade.getCostB(), c2X + 1, centerY - 9, mouseX, mouseY);
        }

        graphics.drawString(font, "➔", centerX - 8, centerY - 6, 0xFFFFFFFF);

        // Result Slot
        int resX = centerX + 20;
        drawSlot(graphics, resX, centerY - 10);
        graphics.renderItem(trade.getResult(), resX + 1, centerY - 9);
        graphics.renderItemDecorations(font, trade.getResult(), resX + 1, centerY - 9);
        checkSlotHover(trade.getResult(), resX + 1, centerY - 9, mouseX, mouseY);

        graphics.drawCenteredString(font, "Villager & Wandering Trader Offers", centerX, centerY + 26, 0xFFAAAAAA);
    }

    private void renderMobDropsView(GuiGraphics graphics, int currentMobDropIndex, int mouseX, int mouseY) {
        if (mobDrops.isEmpty()) {
            graphics.drawCenteredString(font, "No mob drops found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

        if (currentMobDropIndex >= mobDrops.size()) currentMobDropIndex = 0;
        MobDropEntry drop = mobDrops.get(currentMobDropIndex);
        int centerX = guiLeft + guiWidth / 2;
        int centerY = guiTop + 115;

        String pageInfo = "Drop " + (currentMobDropIndex + 1) + " of " + mobDrops.size();
        graphics.drawCenteredString(font, pageInfo, guiLeft + guiWidth / 2, guiTop + 45, 0xFFFFFFFF);

        // Mob Icon & Name
        int mobX = centerX - 64;
        drawSlot(graphics, mobX, centerY - 10);
        graphics.renderItem(drop.getRepresentativeIcon(), mobX + 1, centerY - 9);
        checkSlotHover(drop.getRepresentativeIcon(), mobX + 1, centerY - 9, mouseX, mouseY);

        graphics.drawString(font, drop.getMobName(), centerX - 40, centerY - 18, 0xFFFFCC00);
        graphics.drawString(font, "➔", centerX + 2, centerY - 6, 0xFFFFFFFF);

        // Drop Item Slot
        int dropX = centerX + 30;
        drawSlot(graphics, dropX, centerY - 10);
        graphics.renderItem(drop.getDropItem(), dropX + 1, centerY - 9);
        graphics.renderItemDecorations(font, drop.getDropItem(), dropX + 1, centerY - 9);
        checkSlotHover(drop.getDropItem(), dropX + 1, centerY - 9, mouseX, mouseY);

        graphics.drawCenteredString(font, "Chance: " + drop.getDropChance(), centerX, centerY + 26, 0xFFAAAAAA);
    }

    private void renderTreeView(GuiGraphics graphics, int mouseX, int mouseY) {
        if (cachedTree == null) return;

        int startX = guiLeft + 12;
        int startY = guiTop + 65;

        graphics.drawString(font, "Crafting Tree (Decomposition):", startX, startY, 0xFFFFAA00);

        int lineY = startY + 14;
        lineY = renderTreeNode(graphics, cachedTree, startX, lineY, 0, mouseX, mouseY);

        int summaryY = guiTop + guiHeight - 48;
        graphics.fill(guiLeft + 8, summaryY, guiLeft + guiWidth - 8, guiTop + guiHeight - 8, 0xFF2A2A35);
        graphics.renderOutline(guiLeft + 8, summaryY, guiWidth - 16, 40, 0xFF4A4A5A);
        graphics.drawString(font, "Total Raw Materials Needed:", guiLeft + 12, summaryY + 3, 0xFF55FF55);

        Map<Item, Integer> raw = cachedTree.getRawMaterialsSummary();
        int slotX = guiLeft + 12;
        int slotY = summaryY + 16;

        for (Map.Entry<Item, Integer> entry : raw.entrySet()) {
            if (slotX + 18 > guiLeft + guiWidth - 12) break;

            ItemStack rawStack = new ItemStack(entry.getKey(), entry.getValue());
            drawSlot(graphics, slotX, slotY);
            graphics.renderItem(rawStack, slotX + 1, slotY + 1);
            graphics.renderItemDecorations(font, rawStack, slotX + 1, slotY + 1);
            checkSlotHover(rawStack, slotX + 1, slotY + 1, mouseX, mouseY);

            slotX += 20;
        }
    }

    private int renderTreeNode(GuiGraphics graphics, CraftingTreeNode node, int x, int y, int depth, int mouseX, int mouseY) {
        if (y > guiTop + guiHeight - 65) return y;

        int indent = depth * 14;
        String prefix = depth == 0 ? "● " : "↳ ";
        graphics.drawString(font, prefix, x + indent, y + 2, 0xFFAAAAAA);

        int itemX = x + indent + 12;
        graphics.renderItem(node.getItem(), itemX, y - 2);
        checkSlotHover(node.getItem(), itemX, y - 2, mouseX, mouseY);

        String text = node.getItem().getHoverName().getString() + " x" + node.getCount();
        if (node.isBaseMaterial()) {
            text += " (Base)";
        }
        graphics.drawString(font, text, itemX + 18, y + 2, node.isBaseMaterial() ? 0xFF55FF55 : 0xFFFFFFFF);

        y += 14;

        for (CraftingTreeNode child : node.getChildren()) {
            y = renderTreeNode(graphics, child, x, y, depth + 1, mouseX, mouseY);
        }

        return y;
    }

    @SuppressWarnings("unchecked")
    private <T> void renderCustomCategoryView(GuiGraphics graphics, IDexRecipeCategory<?> categoryRaw, int currentIdx, int mouseX, int mouseY) {
        IDexRecipeCategory<T> category = (IDexRecipeCategory<T>) categoryRaw;
        List<DexRegistriesImpl.CustomRecipeEntry> entries = customCategories.get(categoryRaw);
        if (entries == null || entries.isEmpty()) {
            graphics.drawCenteredString(font, "No custom machine recipes found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

        if (currentIdx >= entries.size()) currentIdx = 0;
        DexRegistriesImpl.CustomRecipeEntry entry = entries.get(currentIdx);
        T recipe = (T) entry.recipe();

        String pageInfo = "Recipe " + (currentIdx + 1) + " of " + entries.size();
        graphics.drawCenteredString(font, pageInfo, guiLeft + guiWidth / 2, guiTop + 45, 0xFFFFFFFF);

        String title = category.getTitle() != null ? category.getTitle().getString() : category.getId().getPath();
        graphics.drawCenteredString(font, title + " (" + category.getId().getNamespace() + ")",
                guiLeft + guiWidth / 2, guiTop + 58, 0xFF88AAFF);

        int centerX = guiLeft + guiWidth / 2;
        int centerY = guiTop + 115;

        // Draw custom background/category graphics
        try {
            category.draw(recipe, graphics, mouseX, mouseY);
        } catch (Throwable ignored) {
        }

        // Render slots
        List<DexRecipeSlot> slots = entry.slots();
        if (slots != null && !slots.isEmpty()) {
            // Calculate slot offset based on category display size
            int offX = centerX - 60;
            int offY = centerY - 30;

            for (DexRecipeSlot slot : slots) {
                int sX = offX + slot.x();
                int sY = offY + slot.y();
                drawSlot(graphics, sX, sY);

                if (!slot.items().isEmpty()) {
                    int cycleIdx = (int) ((System.currentTimeMillis() / 1000) % slot.items().size());
                    ItemStack stack = slot.items().get(cycleIdx);
                    graphics.renderItem(stack, sX + 1, sY + 1);
                    graphics.renderItemDecorations(font, stack, sX + 1, sY + 1);
                    checkSlotHover(stack, sX + 1, sY + 1, mouseX, mouseY);
                }
            }
        }
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF373742);
        graphics.renderOutline(x, y, 18, 18, 0xFF5C5C70);
    }

    private void renderIngredient(GuiGraphics graphics, Ingredient ingredient, int x, int y, int mouseX, int mouseY) {
        if (ingredient == null || ingredient.isEmpty()) return;
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return;

        int index = (int) ((System.currentTimeMillis() / 1000) % items.length);
        ItemStack stack = items[index];

        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        checkSlotHover(stack, x, y, mouseX, mouseY);
    }

    private void checkSlotHover(ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            this.hoveredSlotItem = stack;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!hoveredSlotItem.isEmpty()) {
            if (button == 0) {
                openRecipes(hoveredSlotItem);
                return true;
            } else if (button == 1) {
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
            goBack();
            return true;
        }

        // Hovered Slot Shortcuts: R (Recipes), U (Usages), A (Pin to Bookmarks)
        if (!hoveredSlotItem.isEmpty()) {
            if (keyCode == 82) { // R
                openRecipes(hoveredSlotItem);
                return true;
            } else if (keyCode == 85) { // U
                openUsages(hoveredSlotItem);
                return true;
            } else if (keyCode == 65) { // A
                boolean added = BookmarkManager.getInstance().toggleBookmark(hoveredSlotItem);
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
                    tabRecipeIndices.put(activeTab.id, cur - 1);
                    return true;
                } else if (keyCode == 262 && cur < activeTab.recipeCount - 1) { // Right arrow
                    tabRecipeIndices.put(activeTab.id, cur + 1);
                    return true;
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
