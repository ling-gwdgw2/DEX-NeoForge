package com.dex.client.gui.overlay;

import com.dex.catalog.ItemCatalogManager;
import com.dex.catalog.ModInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ModSidebarWidget {
    public static final int TAB_SIZE = 20;
    public static final int WIDTH = 22;

    private int x;
    private int y;
    private int height;
    private int scrollOffset = 0;

    private ModInfo hoveredMod = null;

    public void updateBounds(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ItemCatalogManager catalog = ItemCatalogManager.getInstance();
        List<ModInfo> mods = catalog.getSortedModList();
        if (mods.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        hoveredMod = null;

        int visibleCount = (height - 16) / TAB_SIZE;
        if (visibleCount < 1) visibleCount = 1;

        int maxScroll = Math.max(0, mods.size() - visibleCount);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        // Background strip
        graphics.fill(x - 1, y, x + WIDTH - 1, y + height, 0x88000000);
        graphics.renderOutline(x - 1, y, WIDTH, height, 0x44FFFFFF);

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(font, "▲", x + WIDTH / 2 - 1, y + 1, 0xAAAAAA);
        }
        if (scrollOffset < maxScroll) {
            graphics.drawCenteredString(font, "▼", x + WIDTH / 2 - 1, y + height - 9, 0xAAAAAA);
        }

        int startY = y + 8;
        String selectedModId = catalog.getSelectedModId();

        for (int i = 0; i < visibleCount && (i + scrollOffset) < mods.size(); i++) {
            int modIndex = i + scrollOffset;
            ModInfo mod = mods.get(modIndex);
            int tabY = startY + (i * TAB_SIZE);

            boolean isSelected = mod.getModId().equals(selectedModId);
            boolean isHovered = mouseX >= x && mouseX < (x + WIDTH) && mouseY >= tabY && mouseY < (tabY + TAB_SIZE);

            if (isHovered) {
                hoveredMod = mod;
            }

            // Highlight background
            if (isSelected) {
                graphics.fill(x, tabY, x + WIDTH - 2, tabY + TAB_SIZE - 1, 0x6644AAFF);
                graphics.renderOutline(x, tabY, WIDTH - 2, TAB_SIZE - 1, 0xFF44AAFF);
            } else if (isHovered) {
                graphics.fill(x, tabY, x + WIDTH - 2, tabY + TAB_SIZE - 1, 0x44FFFFFF);
            }

            // Draw Mod Representative Item Icon
            ItemStack icon = mod.getRepresentativeItem();
            graphics.renderItem(icon, x + 2, tabY + 2);
        }
    }

    public void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredMod != null) {
            Minecraft mc = Minecraft.getInstance();
            List<Component> tooltips = new ArrayList<>();
            tooltips.add(Component.literal(hoveredMod.getDisplayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            
            if (!ItemCatalogManager.ALL_MODS_ID.equals(hoveredMod.getModId())) {
                tooltips.add(Component.literal("@" + hoveredMod.getModId()).withStyle(ChatFormatting.DARK_AQUA));
                tooltips.add(Component.literal(hoveredMod.getItemCount() + " items").withStyle(ChatFormatting.GRAY));
            } else {
                tooltips.add(Component.literal("Showing all registered items").withStyle(ChatFormatting.GRAY));
            }
            
            tooltips.add(Component.literal("Click to filter by this mod").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

            graphics.renderComponentTooltip(mc.font, tooltips, mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        ItemCatalogManager catalog = ItemCatalogManager.getInstance();
        List<ModInfo> mods = catalog.getSortedModList();
        if (mods.isEmpty()) return false;

        if (mouseX < x || mouseX >= (x + WIDTH) || mouseY < y || mouseY >= (y + height)) {
            return false;
        }

        int visibleCount = (height - 16) / TAB_SIZE;
        int maxScroll = Math.max(0, mods.size() - visibleCount);

        // Click top arrow
        if (mouseY < y + 8 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        }
        // Click bottom arrow
        if (mouseY > y + height - 8 && scrollOffset < maxScroll) {
            scrollOffset++;
            return true;
        }

        int startY = y + 8;
        int clickedIndex = (int) ((mouseY - startY) / TAB_SIZE) + scrollOffset;
        if (clickedIndex >= 0 && clickedIndex < mods.size()) {
            ModInfo mod = mods.get(clickedIndex);
            catalog.selectMod(mod.getModId());
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= x && mouseX < (x + WIDTH) && mouseY >= y && mouseY < (y + height)) {
            ItemCatalogManager catalog = ItemCatalogManager.getInstance();
            int visibleCount = (height - 16) / TAB_SIZE;
            int maxScroll = Math.max(0, catalog.getSortedModList().size() - visibleCount);

            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset = Math.max(0, scrollOffset - 2);
                return true;
            } else if (scrollY < 0 && scrollOffset < maxScroll) {
                scrollOffset = Math.min(maxScroll, scrollOffset + 2);
                return true;
            }
        }
        return false;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return WIDTH;
    }
}
