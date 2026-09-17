package com.dex.client.gui.overlay;

import com.dex.catalog.ItemCatalogManager;
import com.dex.catalog.ModInfo;
import com.dex.client.gui.recipe.RecipeViewerScreen;
import com.dex.client.util.DexSoundHelper;
import com.dex.recipe.RecipeIndexManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemGridOverlay {
    public static final int SLOT_SIZE = 18;

    private int x;
    private int y;
    private int width;
    private int height;

    private int columns = 8;
    private int rows = 10;
    private int currentPage = 0;

    private EditBox searchBox;
    private ItemStack hoveredStack = ItemStack.EMPTY;

    public void init(Minecraft mc, int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        // Calculate columns and rows fitting in width and height
        this.columns = Math.max(2, (width - 4) / SLOT_SIZE);
        // Leave 20px at bottom for search box and 14px for pagination bar
        int gridAvailableHeight = height - 38;
        this.rows = Math.max(2, gridAvailableHeight / SLOT_SIZE);

        boolean wasFocused = (this.searchBox != null && this.searchBox.isFocused());
        String currentText = (this.searchBox != null) ? this.searchBox.getValue() : ItemCatalogManager.getInstance().getSearchQuery();

        Font font = mc.font;
        int searchY = y + height - 18;
        int searchWidth = width - 38;
        this.searchBox = new EditBox(font, x + 2, searchY, searchWidth, 16, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search... (@mod, #tag, -neg)").withStyle(ChatFormatting.DARK_GRAY));
        this.searchBox.setValue(currentText);
        this.searchBox.setFocused(wasFocused);
        this.searchBox.setCanLoseFocus(true);
        this.searchBox.setResponder(text -> {
            ItemCatalogManager.getInstance().setSearchQuery(text);
            currentPage = 0;
        });

        clampPage();
    }

    private void clampPage() {
        int totalPages = getTotalPages();
        if (currentPage >= totalPages) {
            currentPage = Math.max(0, totalPages - 1);
        }
        if (currentPage < 0) {
            currentPage = 0;
        }
    }

    public int getPageSize() {
        return columns * rows;
    }

    public int getTotalPages() {
        int items = ItemCatalogManager.getInstance().getCurrentFilteredItems().size();
        int pageSize = getPageSize();
        if (pageSize <= 0) return 1;
        return Math.max(1, (int) Math.ceil((double) items / pageSize));
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clampPage();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        hoveredStack = ItemStack.EMPTY;

        // 1. Background panel
        graphics.fill(x, y, x + width, y + height, 0x88000000);
        graphics.renderOutline(x, y, width, height, 0x44FFFFFF);

        // 2. Pagination bar at top
        int totalPages = getTotalPages();
        String pageText = (currentPage + 1) + " / " + totalPages;

        boolean prevHovered = mouseX >= x + 4 && mouseX < x + 16 && mouseY >= y + 3 && mouseY < y + 15;
        boolean nextHovered = mouseX >= (x + width - 16) && mouseX < (x + width - 4) && mouseY >= y + 3 && mouseY < y + 15;

        graphics.drawString(font, "<", x + 6, y + 4, prevHovered ? 0xFFFFAA00 : 0xFFFFFFFF);
        graphics.drawCenteredString(font, pageText, x + width / 2, y + 4, 0xFFE0E0E0);
        graphics.drawString(font, ">", x + width - 14, y + 4, nextHovered ? 0xFFFFAA00 : 0xFFFFFFFF);

        // 3. Render Item Grid
        List<ItemStack> items = ItemCatalogManager.getInstance().getCurrentFilteredItems();
        int startIndex = currentPage * getPageSize();
        int gridStartY = y + 16;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                int slotX = x + 2 + (c * SLOT_SIZE);
                int slotY = gridStartY + (r * SLOT_SIZE);

                // Slot background
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x33FFFFFF);

                int itemIndex = startIndex + (r * columns + c);
                if (itemIndex < items.size()) {
                    ItemStack stack = items.get(itemIndex);
                    graphics.renderItem(stack, slotX, slotY);
                    graphics.renderItemDecorations(font, stack, slotX, slotY);

                    // If item is pinned/bookmarked, draw a gold badge in the top-right corner
                    if (com.dex.client.bookmark.BookmarkManager.getInstance().isBookmarked(stack)) {
                        graphics.fill(slotX + 12, slotY, slotX + 16, slotY + 4, 0xFFFFCC00);
                    }

                    // Check hover
                    if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                        hoveredStack = stack;
                        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x44FFFFFF);
                    }
                }
            }
        }

        // 4. Render Search Box
        if (searchBox != null) {
            searchBox.render(graphics, mouseX, mouseY, partialTick);
        }

        // 5. Render Clear Search Button (✖)
        int clearBtnX = x + width - 35;
        int clearBtnY = y + height - 18;
        int clearBtnWidth = 13;
        int clearBtnHeight = 16;
        boolean hasSearchText = searchBox != null && !searchBox.getValue().isEmpty();
        if (hasSearchText) {
            boolean clearHovered = mouseX >= clearBtnX && mouseX < clearBtnX + clearBtnWidth && mouseY >= clearBtnY && mouseY < clearBtnY + clearBtnHeight;
            graphics.fill(clearBtnX, clearBtnY, clearBtnX + clearBtnWidth, clearBtnY + clearBtnHeight, clearHovered ? 0x80552222 : 0x5033333C);
            graphics.renderOutline(clearBtnX, clearBtnY, clearBtnWidth, clearBtnHeight, clearHovered ? 0xFFFF5555 : 0xFF555566);
            graphics.drawCenteredString(font, "✖", clearBtnX + clearBtnWidth / 2, clearBtnY + 4, clearHovered ? 0xFFFF7777 : 0xFFAAAAAA);
        }

        // 6. Render Cheat Mode Toggle Button (⚡)
        int cheatBtnX = x + width - 20;
        int cheatBtnY = y + height - 18;
        boolean cheatPermitted = com.dex.client.cheat.CheatGiveHelper.canCheat();
        boolean cheatActive = com.dex.client.config.DEXConfig.getInstance().isCheatMode() && cheatPermitted;
        boolean cheatHovered = mouseX >= cheatBtnX && mouseX < cheatBtnX + 18 && mouseY >= cheatBtnY && mouseY < cheatBtnY + 16;

        if (!cheatPermitted) {
            // Locked appearance in Survival mode without OP
            graphics.fill(cheatBtnX, cheatBtnY, cheatBtnX + 18, cheatBtnY + 16, 0x401A1A22);
            graphics.renderOutline(cheatBtnX, cheatBtnY, 18, 16, 0x30555566);
            graphics.drawCenteredString(font, "⚡", cheatBtnX + 9, cheatBtnY + 4, 0xFF555566);
        } else {
            graphics.fill(cheatBtnX, cheatBtnY, cheatBtnX + 18, cheatBtnY + 16, cheatActive ? 0xD0442A00 : 0x80222228);
            graphics.renderOutline(cheatBtnX, cheatBtnY, 18, 16, cheatActive ? 0xFFFFBB00 : (cheatHovered ? 0xFFFFFFFF : 0xFF555566));
            graphics.drawCenteredString(font, "⚡", cheatBtnX + 9, cheatBtnY + 4, cheatActive ? 0xFFFFDD33 : (cheatHovered ? 0xFFE0E0E0 : 0xFF888899));
        }
    }

    public void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!hoveredStack.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            List<Component> tooltips = new ArrayList<>(mc.screen != null ? mc.screen.getTooltipFromItem(mc, hoveredStack) : List.of());

            // Add Origin Mod information
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(hoveredStack.getItem());
            String namespace = key.getNamespace();
            ModInfo modInfo = ItemCatalogManager.getInstance().getModInfo(namespace);
            String modDisplay = modInfo != null ? modInfo.getDisplayName() : namespace;

            tooltips.add(Component.empty());
            tooltips.add(Component.literal("Origin: ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(modDisplay).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC)));

            // Add Recipe/Usage/Pin hints
            boolean hasRecipes = RecipeIndexManager.getInstance().hasRecipes(hoveredStack);
            boolean hasUsages = RecipeIndexManager.getInstance().hasUsages(hoveredStack);

            if (hasRecipes || hasUsages) {
                Component hint = Component.literal("[ ")
                        .append(Component.literal("R").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(": Recipes | ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("U").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(": Usages | ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("A").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(": Pin ]").withStyle(ChatFormatting.GRAY));
                tooltips.add(hint);
            }

            graphics.renderComponentTooltip(mc.font, tooltips, mouseX, mouseY);
        } else {
            // Check hover on Clear Search button
            int clearBtnX = x + width - 35;
            int clearBtnY = y + height - 18;
            boolean hasSearchText = searchBox != null && !searchBox.getValue().isEmpty();
            if (hasSearchText && mouseX >= clearBtnX && mouseX < clearBtnX + 13 && mouseY >= clearBtnY && mouseY < clearBtnY + 16) {
                List<Component> clearTooltips = List.of(
                        Component.literal("Clear Search (✖)").withStyle(ChatFormatting.RED),
                        Component.literal("Tip: Right-click inside search box to clear").withStyle(ChatFormatting.DARK_GRAY)
                );
                graphics.renderComponentTooltip(Minecraft.getInstance().font, clearTooltips, mouseX, mouseY);
            }

            // Check hover on Cheat Mode button
            int cheatBtnX = x + width - 20;
            int cheatBtnY = y + height - 18;
            if (mouseX >= cheatBtnX && mouseX < cheatBtnX + 18 && mouseY >= cheatBtnY && mouseY < cheatBtnY + 16) {
                boolean cheatPermitted = com.dex.client.cheat.CheatGiveHelper.canCheat();
                boolean cheatActive = com.dex.client.config.DEXConfig.getInstance().isCheatMode() && cheatPermitted;
                List<Component> cheatTooltips;
                if (!cheatPermitted) {
                    cheatTooltips = List.of(
                            Component.literal("Cheat Mode: LOCKED").withStyle(ChatFormatting.RED),
                            Component.literal("Disabled in Survival without Operator permissions (OP / Cheats Enabled).").withStyle(ChatFormatting.GRAY)
                    );
                } else {
                    cheatTooltips = List.of(
                            Component.literal("Cheat Mode: " + (cheatActive ? "ON" : "OFF")).withStyle(cheatActive ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                            Component.literal("• Left-Click item: Give Max Stack").withStyle(ChatFormatting.DARK_GRAY),
                            Component.literal("• Right-Click item: Give 1 Item").withStyle(ChatFormatting.DARK_GRAY),
                            Component.literal("• Or hold Ctrl while clicking items").withStyle(ChatFormatting.DARK_GRAY)
                    );
                }
                graphics.renderComponentTooltip(Minecraft.getInstance().font, cheatTooltips, mouseX, mouseY);
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clear Search Button (✖) click
        int clearBtnX = x + width - 35;
        int clearBtnY = y + height - 18;
        if (searchBox != null && !searchBox.getValue().isEmpty()) {
            if (mouseX >= clearBtnX && mouseX < clearBtnX + 13 && mouseY >= clearBtnY && mouseY < clearBtnY + 16) {
                searchBox.setValue("");
                searchBox.setFocused(true);
                ItemCatalogManager.getInstance().setSearchQuery("");
                currentPage = 0;
                DexSoundHelper.playButtonClick();
                return true;
            }
        }

        // Search box click handling
        if (searchBox != null) {
            boolean isInsideSearch = mouseX >= searchBox.getX() && mouseX < (searchBox.getX() + searchBox.getWidth())
                    && mouseY >= searchBox.getY() && mouseY < (searchBox.getY() + searchBox.getHeight());

            if (isInsideSearch) {
                if (button == 0) { // Left click: focus and place cursor
                    searchBox.setFocused(true);
                    searchBox.mouseClicked(mouseX, mouseY, button);
                    return true;
                } else if (button == 1) { // Right click: clear query and focus
                    searchBox.setValue("");
                    searchBox.setFocused(true);
                    ItemCatalogManager.getInstance().setSearchQuery("");
                    currentPage = 0;
                    DexSoundHelper.playButtonClick();
                    return true;
                }
            } else {
                // Clicked outside search box
                if (button == 0 || button == 1) {
                    searchBox.setFocused(false);
                }
            }
        }

        // Pagination buttons
        if (mouseY >= y + 3 && mouseY < y + 15) {
            if (mouseX >= x + 4 && mouseX < x + 16) {
                if (currentPage > 0) {
                    currentPage--;
                    DexSoundHelper.playButtonClick();
                    return true;
                }
            } else if (mouseX >= (x + width - 16) && mouseX < (x + width - 4)) {
                if (currentPage < getTotalPages() - 1) {
                    currentPage++;
                    DexSoundHelper.playButtonClick();
                    return true;
                }
            }
        }

        // Cheat Mode Toggle button click
        int cheatBtnX = x + width - 20;
        int cheatBtnY = y + height - 18;
        if (mouseX >= cheatBtnX && mouseX < cheatBtnX + 18 && mouseY >= cheatBtnY && mouseY < cheatBtnY + 16) {
            Minecraft mc = Minecraft.getInstance();
            if (!com.dex.client.cheat.CheatGiveHelper.canCheat()) {
                DexSoundHelper.playButtonClick(0.6F);
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("DEX: Cheat Mode requires Operator permissions (OP / Cheats Enabled) in Survival mode!")
                                    .withStyle(ChatFormatting.RED),
                            true
                    );
                }
                return true;
            }

            boolean active = com.dex.client.config.DEXConfig.getInstance().toggleCheatMode();
            DexSoundHelper.playButtonClick(active ? 1.2F : 0.8F);
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("DEX: Cheat Mode " + (active ? "ENABLED" : "DISABLED"))
                                .withStyle(active ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                        true
                );
            }
            return true;
        }

        // Item click
        if (!hoveredStack.isEmpty()) {
            boolean isCheat = com.dex.client.cheat.CheatGiveHelper.canCheat() &&
                    (com.dex.client.config.DEXConfig.getInstance().isCheatMode() || net.minecraft.client.gui.screens.Screen.hasControlDown());
            if (isCheat) {
                if (button == 0) {
                    com.dex.client.cheat.CheatGiveHelper.give(hoveredStack, true);
                    DexSoundHelper.playButtonClick();
                    return true;
                } else if (button == 1) {
                    com.dex.client.cheat.CheatGiveHelper.give(hoveredStack, false);
                    DexSoundHelper.playButtonClick();
                    return true;
                }
            } else {
                if (button == 0) {
                    // Left Click -> Recipes
                    DexSoundHelper.playButtonClick();
                    RecipeViewerScreen.openRecipes(hoveredStack);
                    return true;
                } else if (button == 1) {
                    // Right Click -> Usages
                    DexSoundHelper.playButtonClick();
                    RecipeViewerScreen.openUsages(hoveredStack);
                    return true;
                }
            }
        }

        return mouseX >= x && mouseX < (x + width) && mouseY >= y && mouseY < (y + height);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= x && mouseX < (x + width) && mouseY >= y && mouseY < (y + height)) {
            if (scrollY > 0 && currentPage > 0) {
                currentPage--;
                return true;
            } else if (scrollY < 0 && currentPage < getTotalPages() - 1) {
                currentPage++;
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == 256) { // Escape key unfocuses search box
                searchBox.setFocused(false);
                return true;
            }
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            // Consume all key presses while search box is focused so 'E', 'Q', '1'-'9' don't close screen or drop items
            return true;
        }

        // Check R (keyCode 82), U (keyCode 85), or A (keyCode 65) while hovering over item
        if (!hoveredStack.isEmpty()) {
            if (keyCode == 82) { // R
                RecipeViewerScreen.openRecipes(hoveredStack);
                return true;
            } else if (keyCode == 85) { // U
                RecipeViewerScreen.openUsages(hoveredStack);
                return true;
            } else if (keyCode == 65) { // A: Toggle Bookmark
                boolean added = com.dex.client.bookmark.BookmarkManager.getInstance().toggleBookmark(hoveredStack);
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("DEX: " + (added ? "Pinned " : "Unpinned ") + hoveredStack.getHoverName().getString() + " to Bookmarks!")
                                    .withStyle(added ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                            true
                    );
                }
                return true;
            }
        }

        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (searchBox.charTyped(codePoint, modifiers)) {
                return true;
            }
            return true;
        }
        return false;
    }

    public void unfocusSearch() {
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
    }

    public boolean isSearchFocused() {
        return searchBox != null && searchBox.isFocused();
    }

    public ItemStack getHoveredStack() {
        return hoveredStack;
    }
}
