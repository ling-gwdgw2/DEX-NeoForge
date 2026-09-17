package com.dex.client.ghost;

import com.dex.recipe.RecipeIngredientHelper;
import com.dex.recipe.transfer.RecipeTransferHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.*;

/**
 * Manages rendering translucent ghost items and slot highlights on crafting container screens
 * (Crafting Table and Player 2x2 Inventory) when viewing or transferring recipes.
 */
public class GhostRecipeManager {
    private static final GhostRecipeManager INSTANCE = new GhostRecipeManager();

    public static GhostRecipeManager getInstance() {
        return INSTANCE;
    }

    public static class GhostSlot {
        private final int craftingSlotIndex;   // Slot index in CraftingMenu (1-9)
        private final int inventorySlotIndex;  // Slot index in InventoryMenu (1-4), or -1 if recipe > 2x2
        private final Ingredient ingredient;
        private final ItemStack[] possibleItems;
        private final boolean isMissing;

        public GhostSlot(int craftingSlotIndex, int inventorySlotIndex, Ingredient ingredient, boolean isMissing) {
            this.craftingSlotIndex = craftingSlotIndex;
            this.inventorySlotIndex = inventorySlotIndex;
            this.ingredient = ingredient;
            this.possibleItems = (ingredient != null && !ingredient.isEmpty()) ? ingredient.getItems() : new ItemStack[0];
            this.isMissing = isMissing;
        }

        public int getCraftingSlotIndex() {
            return craftingSlotIndex;
        }

        public int getInventorySlotIndex() {
            return inventorySlotIndex;
        }

        public Ingredient getIngredient() {
            return ingredient;
        }

        public boolean isMissing() {
            return isMissing;
        }

        public ItemStack getDisplayItem() {
            if (possibleItems == null || possibleItems.length == 0) {
                return ItemStack.EMPTY;
            }
            // Smoothly cycle through matching item variants every 1.2 seconds
            int idx = (int) ((System.currentTimeMillis() / 1200L + craftingSlotIndex) % possibleItems.length);
            return possibleItems[idx];
        }
    }

    private RecipeHolder<?> activeRecipe = null;
    private final Map<Integer, GhostSlot> activeGhostSlots = new LinkedHashMap<>();

    private GhostSlot hoveredGhostSlot = null;
    private ItemStack hoveredGhostStack = ItemStack.EMPTY;

    private GhostRecipeManager() {}

    /**
     * Sets the active ghost recipe based on a crafting recipe holder and inventory check result.
     */
    public synchronized void setGhostRecipe(RecipeHolder<?> holder, RecipeTransferHelper.InventoryCheckResult check) {
        if (holder == null || !(holder.value() instanceof CraftingRecipe crafting)) {
            clear();
            return;
        }

        this.activeRecipe = holder;
        this.activeGhostSlots.clear();

        List<Ingredient> rawIngredients = RecipeIngredientHelper.getRawIngredients(crafting);
        if (rawIngredients.isEmpty()) return;

        int width = 3;
        int height = 3;
        boolean fitsIn2x2 = true;

        if (crafting instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
            if (width > 2 || height > 2) {
                fitsIn2x2 = false;
            }
        } else {
            // Shapeless recipe: fits in 2x2 only if at most 4 ingredients
            if (rawIngredients.size() > 4) {
                fitsIn2x2 = false;
            }
        }

        int ingIdx = 0;
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                if (ingIdx < rawIngredients.size()) {
                    Ingredient ing = rawIngredients.get(ingIdx);
                    if (ing != null && !ing.isEmpty()) {
                        int craftingSlotIndex = 1 + (r * 3) + c;
                        int inventorySlotIndex = (fitsIn2x2 && r < 2 && c < 2) ? (1 + (r * 2) + c) : -1;
                        boolean isMissing = check != null && check.missingSlotIndices().contains(ingIdx);

                        activeGhostSlots.put(craftingSlotIndex, new GhostSlot(
                                craftingSlotIndex,
                                inventorySlotIndex,
                                ing,
                                isMissing
                        ));
                    }
                    ingIdx++;
                }
            }
        }
    }

    public synchronized void clear() {
        this.activeRecipe = null;
        this.activeGhostSlots.clear();
        this.hoveredGhostSlot = null;
        this.hoveredGhostStack = ItemStack.EMPTY;
    }

    public synchronized boolean hasGhostRecipe() {
        return activeRecipe != null && !activeGhostSlots.isEmpty();
    }

    public synchronized RecipeHolder<?> getActiveRecipe() {
        return activeRecipe;
    }

    /**
     * Renders translucent ghost items and slot highlights on the crafting container screen.
     */
    public void renderGhostSlots(GuiGraphics graphics, AbstractContainerScreen<?> container, int mouseX, int mouseY) {
        if (!hasGhostRecipe() || container == null) return;

        AbstractContainerMenu menu = container.getMenu();
        if (menu == null) return;

        boolean isCraftingTable = menu instanceof CraftingMenu;
        boolean isInventory = menu instanceof InventoryMenu;

        if (!isCraftingTable && !isInventory) {
            return;
        }

        // Auto-dismiss if all ghost slots now have real items placed in them
        boolean allFilled = true;
        for (GhostSlot ghostSlot : activeGhostSlots.values()) {
            int targetIdx = isInventory ? ghostSlot.getInventorySlotIndex() : ghostSlot.getCraftingSlotIndex();
            if (targetIdx >= 0 && targetIdx < menu.slots.size()) {
                if (!menu.slots.get(targetIdx).hasItem()) {
                    allFilled = false;
                    break;
                }
            }
        }
        if (allFilled) {
            clear();
            return;
        }

        int guiLeft = container.getGuiLeft();
        int guiTop = container.getGuiTop();

        hoveredGhostSlot = null;
        hoveredGhostStack = ItemStack.EMPTY;

        for (GhostSlot ghostSlot : activeGhostSlots.values()) {
            int targetSlotIndex = isInventory ? ghostSlot.getInventorySlotIndex() : ghostSlot.getCraftingSlotIndex();
            if (targetSlotIndex < 0 || targetSlotIndex >= menu.slots.size()) continue;

            Slot slot = menu.slots.get(targetSlotIndex);
            if (slot == null || slot.hasItem()) {
                // Real item already occupies slot, do not render ghost overlay
                continue;
            }

            int slotX = guiLeft + slot.x;
            int slotY = guiTop + slot.y;

            // 1. Slot Glassmorphism Tint & Border Highlight
            if (ghostSlot.isMissing()) {
                // Soft translucent red tint
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x35FF3333);
                graphics.renderOutline(slotX, slotY, 16, 16, 0x80FF4444);
            } else {
                // Soft translucent emerald tint
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x2533FF33);
                graphics.renderOutline(slotX, slotY, 16, 16, 0x8033FF33);
            }

            // 2. Translucent Ghost Item
            ItemStack displayStack = ghostSlot.getDisplayItem();
            if (!displayStack.isEmpty()) {
                graphics.renderFakeItem(displayStack, slotX, slotY);
                graphics.fill(RenderType.guiGhostRecipeOverlay(), slotX, slotY, slotX + 16, slotY + 16, 0x30FFFFFF);
            }

            // 3. Hover detection for detailed tooltip
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                hoveredGhostSlot = ghostSlot;
                hoveredGhostStack = displayStack;
            }
        }
    }

    /**
     * Renders tooltip for hovered ghost slot at the top rendering layer.
     */
    public void renderTooltip(GuiGraphics graphics, AbstractContainerScreen<?> container, int mouseX, int mouseY) {
        if (hoveredGhostSlot == null || hoveredGhostStack.isEmpty() || container == null) return;

        List<Component> tooltipLines = new ArrayList<>();
        tooltipLines.add(hoveredGhostStack.getHoverName());
        if (hoveredGhostSlot.isMissing()) {
            tooltipLines.add(Component.literal("✖ Missing Ingredient").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        } else {
            tooltipLines.add(Component.literal("✔ Available in Inventory").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
        }
        tooltipLines.add(Component.literal("DEX Ghost Recipe (Right-Click to dismiss)").withStyle(ChatFormatting.DARK_GRAY));

        graphics.renderComponentTooltip(Minecraft.getInstance().font, tooltipLines, mouseX, mouseY);
    }

    /**
     * Handles slot clicks to dismiss ghost recipe when user interacts with result or right-clicks ghost slot.
     */
    public void onSlotClicked(Slot slot, int mouseButton) {
        if (!hasGhostRecipe() || slot == null) return;

        // If player clicks result slot (slot 0), clear ghost recipe
        if (slot.index == 0) {
            clear();
            return;
        }

        // If right clicked on any ghost slot, dismiss
        if (mouseButton == 1) {
            for (GhostSlot gs : activeGhostSlots.values()) {
                if (gs.getCraftingSlotIndex() == slot.index || gs.getInventorySlotIndex() == slot.index) {
                    clear();
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                                Component.literal("DEX: Cleared Ghost Recipe").withStyle(ChatFormatting.GRAY),
                                true
                        );
                    }
                    break;
                }
            }
        }
    }
}
