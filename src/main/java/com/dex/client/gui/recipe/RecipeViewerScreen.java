package com.dex.client.gui.recipe;

import com.dex.catalog.ItemCatalogManager;
import com.dex.catalog.ModInfo;
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
import net.minecraft.world.item.crafting.*;

import java.util.*;

public class RecipeViewerScreen extends Screen {
    public enum Mode {
        CRAFTING,
        USAGE
    }

    public enum Category {
        RECIPES("Recipes"),
        BREWING("Brewing"),
        TRADING("Trading"),
        MOB_DROPS("Mob Drops"),
        TREE("🌳 Tree");

        private final String label;
        Category(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final Screen previousScreen;
    private final ItemStack targetItem;
    private final Mode mode;

    private final List<RecipeHolder<?>> recipes;
    private final List<BrewingRecipeEntry> brewingRecipes;
    private final List<VillagerTradeEntry> villagerTrades;
    private final List<MobDropEntry> mobDrops;

    private Category activeCategory = Category.RECIPES;
    private int currentRecipeIndex = 0;
    private int currentBrewingIndex = 0;
    private int currentTradeIndex = 0;
    private int currentMobDropIndex = 0;

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
        } else {
            this.recipes = new ArrayList<>(RecipeIndexManager.getInstance().getUsagesFor(targetItem));
            this.brewingRecipes = new ArrayList<>(BrewingIndexManager.getInstance().getUsagesFor(targetItem));
            this.villagerTrades = new ArrayList<>(VillagerTradeIndexManager.getInstance().getTradesBuying(targetItem));
            this.mobDrops = new ArrayList<>();
        }

        // Set initial category to first one with content
        if (recipes.isEmpty()) {
            if (!brewingRecipes.isEmpty()) activeCategory = Category.BREWING;
            else if (!villagerTrades.isEmpty()) activeCategory = Category.TRADING;
            else if (!mobDrops.isEmpty()) activeCategory = Category.MOB_DROPS;
            else activeCategory = Category.RECIPES;
        }

        this.cachedTree = CraftingTreeCalculator.calculateTree(targetItem, treeMultiplier);
    }

    public static void openRecipes(ItemStack stack) {
        if (stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new RecipeViewerScreen(mc.screen, stack, Mode.CRAFTING));
    }

    public static void openUsages(ItemStack stack) {
        if (stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new RecipeViewerScreen(mc.screen, stack, Mode.USAGE));
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

        // 1. Category Tabs at Top
        int tabX = guiLeft + 8;
        int tabY = guiTop + 22;
        int tabHeight = 16;

        List<Category> available = new ArrayList<>();
        if (!recipes.isEmpty() || (brewingRecipes.isEmpty() && villagerTrades.isEmpty() && mobDrops.isEmpty())) {
            available.add(Category.RECIPES);
        }
        if (!brewingRecipes.isEmpty()) available.add(Category.BREWING);
        if (!villagerTrades.isEmpty()) available.add(Category.TRADING);
        if (!mobDrops.isEmpty()) available.add(Category.MOB_DROPS);
        if (mode == Mode.CRAFTING && !recipes.isEmpty()) available.add(Category.TREE);

        for (Category cat : available) {
            boolean isActive = cat == activeCategory;
            int btnWidth = font.width(cat.getLabel()) + 10;

            this.addRenderableWidget(Button.builder(
                    Component.literal(cat.getLabel()).withStyle(isActive ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                    b -> {
                        this.activeCategory = cat;
                        rebuildCategoryButtons();
                    }
            ).bounds(tabX, tabY, btnWidth, tabHeight).build());

            tabX += btnWidth + 2;
        }

        // 2. Navigation & Action Buttons depending on Category
        if (activeCategory == Category.RECIPES) {
            // Previous recipe button
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                if (currentRecipeIndex > 0) currentRecipeIndex--;
            }).bounds(guiLeft + 8, guiTop + 42, 18, 16).build());

            // Next recipe button
            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                if (currentRecipeIndex < recipes.size() - 1) currentRecipeIndex++;
            }).bounds(guiLeft + guiWidth - 26, guiTop + 42, 18, 16).build());

            // Auto-transfer '+' button
            this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                transferRecipe();
            }).bounds(guiLeft + guiWidth - 26, guiTop + guiHeight - 24, 18, 16).build());

        } else if (activeCategory == Category.BREWING) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                if (currentBrewingIndex > 0) currentBrewingIndex--;
            }).bounds(guiLeft + 8, guiTop + 42, 18, 16).build());

            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                if (currentBrewingIndex < brewingRecipes.size() - 1) currentBrewingIndex++;
            }).bounds(guiLeft + guiWidth - 26, guiTop + 42, 18, 16).build());

        } else if (activeCategory == Category.TRADING) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                if (currentTradeIndex > 0) currentTradeIndex--;
            }).bounds(guiLeft + 8, guiTop + 42, 18, 16).build());

            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                if (currentTradeIndex < villagerTrades.size() - 1) currentTradeIndex++;
            }).bounds(guiLeft + guiWidth - 26, guiTop + 42, 18, 16).build());

        } else if (activeCategory == Category.MOB_DROPS) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                if (currentMobDropIndex > 0) currentMobDropIndex--;
            }).bounds(guiLeft + 8, guiTop + 42, 18, 16).build());

            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                if (currentMobDropIndex < mobDrops.size() - 1) currentMobDropIndex++;
            }).bounds(guiLeft + guiWidth - 26, guiTop + 42, 18, 16).build());

        } else if (activeCategory == Category.TREE) {
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

        RecipeHolder<?> holder = recipes.get(currentRecipeIndex);

        if (mc.player.containerMenu != null) {
            int containerId = mc.player.containerMenu.containerId;
            boolean shift = Screen.hasShiftDown();

            // Send standard vanilla/neoforge placement packet
            mc.getConnection().send(new ServerboundPlaceRecipePacket(containerId, holder, shift));

            mc.player.displayClientMessage(
                    Component.literal("DEX: Auto-filled recipe to crafting table!").withStyle(ChatFormatting.GREEN),
                    true
            );

            // Return to container screen so player sees the items filled
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

        // Background Box
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight, 0xEE1E1E24);
        graphics.renderOutline(guiLeft, guiTop, guiWidth, guiHeight, 0xFF4A4A5A);

        // Header Title
        String titleStr = (mode == Mode.CRAFTING ? "Crafting: " : "Usages of: ") + targetItem.getHoverName().getString();
        graphics.drawCenteredString(font, titleStr, guiLeft + guiWidth / 2, guiTop + 8, 0xFFFFAA00);

        // Render Active Category
        switch (activeCategory) {
            case RECIPES -> renderRecipesView(graphics, mouseX, mouseY);
            case BREWING -> renderBrewingView(graphics, mouseX, mouseY);
            case TRADING -> renderTradingView(graphics, mouseX, mouseY);
            case MOB_DROPS -> renderMobDropsView(graphics, mouseX, mouseY);
            case TREE -> renderTreeView(graphics, mouseX, mouseY);
        }

        // Render Tooltip for hovered slot item
        if (!hoveredSlotItem.isEmpty()) {
            graphics.renderTooltip(font, hoveredSlotItem, mouseX, mouseY);
        }
    }

    private void renderRecipesView(GuiGraphics graphics, int mouseX, int mouseY) {
        if (recipes.isEmpty()) {
            graphics.drawCenteredString(font, "No recipes found for this item.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

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

    private void renderBrewingView(GuiGraphics graphics, int mouseX, int mouseY) {
        if (brewingRecipes.isEmpty()) {
            graphics.drawCenteredString(font, "No brewing recipes found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

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

    private void renderTradingView(GuiGraphics graphics, int mouseX, int mouseY) {
        if (villagerTrades.isEmpty()) {
            graphics.drawCenteredString(font, "No trading offers found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

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

    private void renderMobDropsView(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mobDrops.isEmpty()) {
            graphics.drawCenteredString(font, "No mob drops found.", guiLeft + guiWidth / 2, guiTop + 90, 0xFFAAAAAA);
            return;
        }

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

        // Drop Chance info
        graphics.drawCenteredString(font, "Chance: " + drop.getDropChance(), centerX, centerY + 26, 0xFFAAAAAA);
    }

    private void renderTreeView(GuiGraphics graphics, int mouseX, int mouseY) {
        if (cachedTree == null) return;

        int startX = guiLeft + 12;
        int startY = guiTop + 65;

        graphics.drawString(font, "Crafting Tree (Decomposition):", startX, startY, 0xFFFFAA00);

        // Render Tree Branch lines
        int lineY = startY + 14;
        lineY = renderTreeNode(graphics, cachedTree, startX, lineY, 0, mouseX, mouseY);

        // Render Base / Raw Materials Summary Box at Bottom
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
        if (keyCode == 256 || keyCode == Minecraft.getInstance().options.keyInventory.getKey().getValue()) {
            Minecraft.getInstance().setScreen(previousScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
