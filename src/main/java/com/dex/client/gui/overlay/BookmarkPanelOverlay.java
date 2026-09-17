package com.dex.client.gui.overlay;

import com.dex.client.bookmark.BookmarkManager;
import com.dex.client.cheat.CheatGiveHelper;
import com.dex.client.config.DEXConfig;
import com.dex.client.gui.recipe.RecipeViewerScreen;
import com.dex.client.util.DexSoundHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Left Bookmark Panel Overlay rendered on the left side of container screens.
 * Displays pinned recipes/items with quick recipe lookup (R/U), cheat give, and middle-click unpinning.
 */
public class BookmarkPanelOverlay {
    public static final int SLOT_SIZE = 18;

    private int x;
    private int y;
    private int width;
    private int height;

    private int columns = 1;
    private int rows = 6;
    private int currentPage = 0;
    private boolean active = false;

    private ItemStack hoveredStack = ItemStack.EMPTY;

    public void updateBounds(Minecraft mc, AbstractContainerScreen<?> container, int screenWidth, int screenHeight) {
        if (!DEXConfig.get().isShowLeftBookmarkPanel()) {
            this.active = false;
            return;
        }

        int guiLeft = container.getGuiLeft();
        int imageWidth = container.getXSize();
        int containerTop = container.getGuiTop();
        int containerHeight = container.getYSize();

        // Calculate container left edge safely
        int calculatedLeft = guiLeft > 0 ? guiLeft : ((screenWidth - Math.max(imageWidth, 176)) / 2);

        // Dock to the far left of the screen (6px margin)
        this.x = 6;
        int availableSpace = calculatedLeft - this.x - 6;

        // Minimum required width is 26px for at least 1 column of 18px slots + 8px padding
        if (availableSpace < 26) {
            this.active = false;
            return;
        }

        this.columns = Math.max(1, Math.min(4, (availableSpace - 8) / SLOT_SIZE));
        this.width = (this.columns * SLOT_SIZE) + 8;

        this.y = 6;
        this.height = screenHeight - 12;

        int gridAvailableHeight = this.height - 24; // 24px reserved for header and margins
        this.rows = Math.max(1, gridAvailableHeight / SLOT_SIZE);
        this.active = true;

        clampPage();
    }

    public boolean isActive() {
        return active && DEXConfig.get().isShowLeftBookmarkPanel();
    }

    public int getPageSize() {
        return Math.max(1, columns * rows);
    }

    public int getTotalPages() {
        int total = BookmarkManager.getInstance().getBookmarks().size();
        int size = getPageSize();
        return Math.max(1, (int) Math.ceil((double) total / size));
    }

    private void clampPage() {
        int max = getTotalPages();
        if (currentPage >= max) {
            currentPage = Math.max(0, max - 1);
        }
        if (currentPage < 0) {
            currentPage = 0;
        }
    }

    public ItemStack getHoveredStack() {
        return hoveredStack;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isActive()) return;
        clampPage();

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        hoveredStack = ItemStack.EMPTY;

        List<ItemStack> bookmarks = BookmarkManager.getInstance().getBookmarks();
        int totalBookmarks = bookmarks.size();
        int totalPages = getTotalPages();

        // 1. Transparent Panel Background (No solid slate box)

        // 2. Header Bar: Gold Star & Item Count
        if (columns >= 2) {
            graphics.drawString(font, "★ Bookmarks (" + totalBookmarks + ")", x + 4, y + 5, 0xFFFFCC00);
        } else {
            graphics.drawString(font, "★ " + totalBookmarks, x + 4, y + 5, 0xFFFFCC00);
        }

        // Header Pagination Buttons if multiple pages
        if (totalPages > 1) {
            int pBtnY = y + 3;
            // Left page button (<)
            int leftBtnX = x + width - 20;
            boolean leftHovered = mouseX >= leftBtnX && mouseX < leftBtnX + 9 && mouseY >= pBtnY && mouseY < pBtnY + 11;
            graphics.drawString(font, "<", leftBtnX, pBtnY, currentPage > 0 ? (leftHovered ? 0xFFFFAA00 : 0xFFFFFFFF) : 0xFF555566);

            // Right page button (>)
            int rightBtnX = x + width - 10;
            boolean rightHovered = mouseX >= rightBtnX && mouseX < rightBtnX + 9 && mouseY >= pBtnY && mouseY < pBtnY + 11;
            graphics.drawString(font, ">", rightBtnX, pBtnY, currentPage < totalPages - 1 ? (rightHovered ? 0xFFFFAA00 : 0xFFFFFFFF) : 0xFF555566);
        }

        // 3. Render Bookmarked Items Grid (Clean transparent floating items)
        int gridStartY = y + 18;
        int startIndex = currentPage * getPageSize();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                int slotX = x + 4 + (c * SLOT_SIZE);
                int slotY = gridStartY + (r * SLOT_SIZE);

                int itemIndex = startIndex + (r * columns + c);
                if (itemIndex < totalBookmarks) {
                    // Subtle translucent slot backing
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x1A000000);
                    graphics.renderOutline(slotX - 1, slotY - 1, 18, 18, 0x25FFFFFF);

                    ItemStack stack = bookmarks.get(itemIndex);
                    graphics.renderItem(stack, slotX, slotY);
                    graphics.renderItemDecorations(font, stack, slotX, slotY);

                    // Gold pin badge in top-right corner
                    graphics.fill(slotX + 12, slotY, slotX + 16, slotY + 4, 0xFFFFCC00);

                    // Check hover
                    if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                        hoveredStack = stack;
                        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x40FFFFFF);
                        graphics.renderOutline(slotX - 1, slotY - 1, 18, 18, 0xFFFFAA00);
                    }
                }
            }
        }

        // 4. Empty State Placeholder
        if (totalBookmarks == 0) {
            int centerX = x + width / 2;
            graphics.drawCenteredString(font, "★", centerX, y + 36, 0x88FFAA00);
            if (columns >= 2) {
                graphics.drawCenteredString(font, "Pinned Items", centerX, y + 52, 0x88AAAAAA);
                graphics.drawCenteredString(font, "Press 'A' on", centerX, y + 72, 0x66FFFFFF);
                graphics.drawCenteredString(font, "any item to pin", centerX, y + 84, 0x66888888);
            } else {
                graphics.drawCenteredString(font, "Empty", centerX, y + 50, 0x66AAAAAA);
                graphics.drawCenteredString(font, "'A' key", centerX, y + 70, 0x66FFAA00);
                graphics.drawCenteredString(font, "to pin", centerX, y + 82, 0x66888888);
            }
        }
    }

    public void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();

        if (!hoveredStack.isEmpty()) {
            List<Component> tooltips = new ArrayList<>(mc.screen != null ? mc.screen.getTooltipFromItem(mc, hoveredStack) : List.of());

            tooltips.add(Component.empty());
            tooltips.add(Component.literal("★ Bookmarked Item").withStyle(ChatFormatting.GOLD));

            boolean isCheat = CheatGiveHelper.canCheat() && (DEXConfig.get().isCheatMode() || Screen.hasControlDown());
            if (isCheat) {
                tooltips.add(Component.literal("• Left-Click: Cheat 64").withStyle(ChatFormatting.DARK_GRAY));
                tooltips.add(Component.literal("• Right-Click: Cheat 1").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                tooltips.add(Component.literal("• Left-Click: Show Recipes (R)").withStyle(ChatFormatting.DARK_GRAY));
                tooltips.add(Component.literal("• Right-Click: Show Usages (U)").withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltips.add(Component.literal("• Middle-Click / 'A': Remove Bookmark").withStyle(ChatFormatting.RED));

            graphics.renderComponentTooltip(mc.font, tooltips, mouseX, mouseY);
        } else if (getTotalPages() > 1 && mouseY >= y + 2 && mouseY < y + 15) {
            int leftBtnX = x + width - 20;
            int rightBtnX = x + width - 10;
            if (mouseX >= leftBtnX && mouseX < leftBtnX + 9 && currentPage > 0) {
                graphics.renderComponentTooltip(mc.font, List.of(Component.literal("Previous Page (" + currentPage + "/" + getTotalPages() + ")")), mouseX, mouseY);
            } else if (mouseX >= rightBtnX && mouseX < rightBtnX + 9 && currentPage < getTotalPages() - 1) {
                graphics.renderComponentTooltip(mc.font, List.of(Component.literal("Next Page (" + (currentPage + 2) + "/" + getTotalPages() + ")")), mouseX, mouseY);
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isActive()) return false;

        boolean isInsidePanel = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        if (!isInsidePanel) return false;

        // 1. Pagination buttons click
        int totalPages = getTotalPages();
        if (totalPages > 1 && mouseY >= y + 2 && mouseY < y + 15) {
            int leftBtnX = x + width - 20;
            int rightBtnX = x + width - 10;
            if (mouseX >= leftBtnX && mouseX < leftBtnX + 9 && currentPage > 0) {
                currentPage--;
                DexSoundHelper.playButtonClick();
                return true;
            } else if (mouseX >= rightBtnX && mouseX < rightBtnX + 9 && currentPage < totalPages - 1) {
                currentPage++;
                DexSoundHelper.playButtonClick();
                return true;
            }
        }

        // 2. Clicked on an item slot
        if (!hoveredStack.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();

            if (button == 2) {
                // Middle click: unpin bookmark
                ItemStack toRemove = hoveredStack;
                BookmarkManager.getInstance().toggleBookmark(toRemove);
                DexSoundHelper.playButtonClick(1.2F);
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("DEX: Unpinned " + toRemove.getHoverName().getString() + " from Bookmarks!").withStyle(ChatFormatting.GRAY),
                            true
                    );
                }
                clampPage();
                return true;
            }

            boolean isCheat = CheatGiveHelper.canCheat() && (DEXConfig.get().isCheatMode() || Screen.hasControlDown());
            if (isCheat) {
                if (button == 0) {
                    CheatGiveHelper.give(hoveredStack, true);
                    DexSoundHelper.playButtonClick();
                    return true;
                } else if (button == 1) {
                    CheatGiveHelper.give(hoveredStack, false);
                    DexSoundHelper.playButtonClick();
                    return true;
                }
            } else {
                if (button == 0) {
                    DexSoundHelper.playButtonClick();
                    RecipeViewerScreen.openRecipes(hoveredStack);
                    return true;
                } else if (button == 1) {
                    DexSoundHelper.playButtonClick();
                    RecipeViewerScreen.openUsages(hoveredStack);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isActive()) return false;

        boolean isInsidePanel = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        if (!isInsidePanel) return false;

        int totalPages = getTotalPages();
        if (totalPages > 1) {
            if (scrollY < 0 && currentPage < totalPages - 1) {
                currentPage++;
                DexSoundHelper.playButtonClick();
                return true;
            } else if (scrollY > 0 && currentPage > 0) {
                currentPage--;
                DexSoundHelper.playButtonClick();
                return true;
            }
        }

        return false;
    }
}
