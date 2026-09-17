package com.dex.client;

import com.dex.DEXMod;
import com.dex.catalog.ItemCatalogManager;
import com.dex.catalog.ModInfo;
import com.dex.client.bookmark.BookmarkManager;
import com.dex.client.ghost.GhostRecipeManager;
import com.dex.client.gui.overlay.BookmarkPanelOverlay;
import com.dex.client.gui.overlay.ItemGridOverlay;
import com.dex.client.gui.overlay.ModSidebarWidget;
import com.dex.client.gui.recipe.RecipeViewerScreen;
import com.dex.client.util.DexSoundHelper;
import com.dex.plugin.DexPluginManager;
import com.dex.recipe.RecipeIndexManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class DEXClientEvents {
    private static final BookmarkPanelOverlay bookmarkPanel = new BookmarkPanelOverlay();
    private static final ModSidebarWidget modSidebar = new ModSidebarWidget();
    private static final ItemGridOverlay itemGrid = new ItemGridOverlay();
    private static boolean overlayActive = false;
    private static boolean overlayVisible = true;

    private static int lastContainerRight = -1;
    private static int lastScreenWidth = -1;
    private static int lastScreenHeight = -1;

    // Reflection cache for reading hovered slot from AbstractContainerScreen safely
    private static Method getSlotUnderMouseMethod = null;
    private static Field hoveredSlotField = null;
    private static boolean reflectionResolved = false;

    private static void resolveSlotReflection() {
        if (reflectionResolved) return;
        reflectionResolved = true;

        try {
            for (Method m : AbstractContainerScreen.class.getDeclaredMethods()) {
                if (Slot.class.isAssignableFrom(m.getReturnType()) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    getSlotUnderMouseMethod = m;
                    break;
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (Field f : AbstractContainerScreen.class.getDeclaredFields()) {
                if (Slot.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    hoveredSlotField = f;
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    public static Slot getHoveredSlot(AbstractContainerScreen<?> container) {
        resolveSlotReflection();
        try {
            if (getSlotUnderMouseMethod != null) {
                return (Slot) getSlotUnderMouseMethod.invoke(container);
            }
            if (hoveredSlotField != null) {
                return (Slot) hoveredSlotField.get(container);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            ItemCatalogManager.getInstance().initialize();
            RecipeIndexManager.getInstance().reindex(mc.level);
            com.dex.recipe.brewing.BrewingIndexManager.getInstance().reindex(mc.level);
            com.dex.recipe.trading.VillagerTradeIndexManager.getInstance().reindex();
            com.dex.recipe.drops.MobDropIndexManager.getInstance().reindex();
            DexPluginManager.getInstance().initializeAll(mc.level);
            BookmarkManager.getInstance().ensureLoaded();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            ItemCatalogManager.getInstance().initialize();
            RecipeIndexManager.getInstance().reindex(mc.level);
            com.dex.recipe.brewing.BrewingIndexManager.getInstance().reindex(mc.level);
            com.dex.recipe.trading.VillagerTradeIndexManager.getInstance().reindex();
            com.dex.recipe.drops.MobDropIndexManager.getInstance().reindex();
            DexPluginManager.getInstance().initializeAll(mc.level);
            BookmarkManager.getInstance().ensureLoaded();
        }
    }

    private static void updateOverlayBounds(AbstractContainerScreen<?> container, int screenWidth, int screenHeight) {
        if (!overlayVisible) {
            overlayActive = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int guiLeft = container.getGuiLeft();
        int imageWidth = container.getXSize();

        // Calculate the actual right edge of the container:
        // A centered container always ends at least at (screenWidth + imageWidth) / 2
        int calculatedRight = guiLeft > 0 ? (guiLeft + imageWidth) : ((screenWidth + Math.max(imageWidth, 195)) / 2);
        int containerRight = Math.max(calculatedRight, (screenWidth + Math.max(imageWidth, 176)) / 2);

        // Add 6px margin to avoid touching any container borders or side tabs
        containerRight += 6;

        int availableWidth = screenWidth - containerRight - 6;

        // Also update Left Bookmark Panel geometry
        bookmarkPanel.updateBounds(mc, container, screenWidth, screenHeight);

        if (availableWidth >= 100) {
            overlayActive = true;
            int sidebarWidth = ModSidebarWidget.WIDTH;
            int sidebarX = containerRight;
            int gridX = sidebarX + sidebarWidth + 2;
            int gridWidth = screenWidth - gridX - 6;

            int overlayY = 6;
            int overlayHeight = screenHeight - 12;

            if (containerRight != lastContainerRight || screenWidth != lastScreenWidth || screenHeight != lastScreenHeight) {
                lastContainerRight = containerRight;
                lastScreenWidth = screenWidth;
                lastScreenHeight = screenHeight;

                modSidebar.updateBounds(sidebarX, overlayY, overlayHeight);
                itemGrid.init(mc, gridX, overlayY, gridWidth, overlayHeight);
            }
        } else {
            overlayActive = false;
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> container) {
            ItemCatalogManager.getInstance().initialize();
            lastContainerRight = -1; // Force layout recalculation
            itemGrid.unfocusSearch();
            updateOverlayBounds(container, event.getScreen().width, event.getScreen().height);

            // Clear ghost recipe if not a crafting container
            if (!(container.getMenu() instanceof CraftingMenu || container.getMenu() instanceof InventoryMenu)) {
                GhostRecipeManager.getInstance().clear();
            }
        } else {
            itemGrid.unfocusSearch();
            overlayActive = false;
        }
    }

    @SubscribeEvent
    public static void onContainerRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> container)) return;

        // Ensure bounds are updated to current container geometry every frame
        updateOverlayBounds(container, event.getScreen().width, event.getScreen().height);

        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        float partialTick = event.getPartialTick();

        // 1. Render Left Bookmark Panel
        bookmarkPanel.render(event.getGuiGraphics(), mouseX, mouseY, partialTick);

        // 2. Render Ghost Recipe Items & Highlights onto Crafting Container
        GhostRecipeManager.getInstance().renderGhostSlots(event.getGuiGraphics(), container, mouseX, mouseY);

        if (overlayActive) {
            // 3. Render Mod Sidebar (Left of grid)
            modSidebar.render(event.getGuiGraphics(), mouseX, mouseY, partialTick);

            // 4. Render Item Grid
            itemGrid.render(event.getGuiGraphics(), mouseX, mouseY, partialTick);

            // 5. Render Tooltips on top
            itemGrid.renderTooltips(event.getGuiGraphics(), mouseX, mouseY);
            modSidebar.renderTooltips(event.getGuiGraphics(), mouseX, mouseY);
        }

        // Render Bookmark Tooltips at top layer
        bookmarkPanel.renderTooltips(event.getGuiGraphics(), mouseX, mouseY);

        // Render Ghost Recipe Tooltip if hovered
        GhostRecipeManager.getInstance().renderTooltip(event.getGuiGraphics(), container, mouseX, mouseY);
    }

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        int button = event.getButton();

        // 1. Check Left Bookmark Panel
        if (bookmarkPanel.mouseClicked(mouseX, mouseY, button)) {
            event.setCanceled(true);
            return;
        }

        // 2. Check Ghost Recipe Slot Interactions (click result or right-click to dismiss)
        if (GhostRecipeManager.getInstance().hasGhostRecipe()) {
            if (event.getScreen() instanceof AbstractContainerScreen<?> container) {
                Slot slot = getHoveredSlot(container);
                if (slot != null) {
                    GhostRecipeManager.getInstance().onSlotClicked(slot, button);
                }
            }
        }

        if (!overlayActive) return;

        if (modSidebar.mouseClicked(mouseX, mouseY, button)) {
            event.setCanceled(true);
            return;
        }

        if (itemGrid.mouseClicked(mouseX, mouseY, button)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        double scrollX = event.getScrollDeltaX();
        double scrollY = event.getScrollDeltaY();

        // 1. Check Left Bookmark Panel
        if (bookmarkPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            event.setCanceled(true);
            return;
        }

        if (!overlayActive) return;

        if (modSidebar.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            event.setCanceled(true);
            return;
        }

        if (itemGrid.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        // Ctrl + O toggles overlay visibility
        if (event.getKeyCode() == 79 && (event.getModifiers() & 2) != 0) {
            overlayVisible = !overlayVisible;
            lastContainerRight = -1;
            DexSoundHelper.playButtonClick();
            event.setCanceled(true);
            return;
        }

        // 1. Check if search box or item grid consumes the key press
        if (overlayActive && itemGrid.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
            return;
        }

        // 2. Check if hovering over Left Bookmark Panel item
        ItemStack bStack = bookmarkPanel.getHoveredStack();
        if (bStack != null && !bStack.isEmpty()) {
            if (event.getKeyCode() == 82) { // R
                DexSoundHelper.playButtonClick();
                RecipeViewerScreen.openRecipes(bStack);
                event.setCanceled(true);
                return;
            } else if (event.getKeyCode() == 85) { // U
                DexSoundHelper.playButtonClick();
                RecipeViewerScreen.openUsages(bStack);
                event.setCanceled(true);
                return;
            } else if (event.getKeyCode() == 65) { // A
                boolean added = BookmarkManager.getInstance().toggleBookmark(bStack);
                DexSoundHelper.playButtonClick(1.2F);
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("DEX: " + (added ? "Pinned " : "Unpinned ") + bStack.getHoverName().getString() + " from Bookmarks!")
                                    .withStyle(added ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                            true
                    );
                }
                event.setCanceled(true);
                return;
            }
        }

        // 3. Check hovered container/inventory slots for R (Recipes), U (Usages), and A (Bookmark)
        if (event.getScreen() instanceof AbstractContainerScreen<?> container) {
            Slot slot = getHoveredSlot(container);
            if (slot != null && slot.hasItem()) {
                ItemStack stack = slot.getItem();
                if (event.getKeyCode() == 82) { // 'R' key
                    DexSoundHelper.playButtonClick();
                    RecipeViewerScreen.openRecipes(stack);
                    event.setCanceled(true);
                    return;
                } else if (event.getKeyCode() == 85) { // 'U' key
                    DexSoundHelper.playButtonClick();
                    RecipeViewerScreen.openUsages(stack);
                    event.setCanceled(true);
                    return;
                } else if (event.getKeyCode() == 65) { // 'A' key: Bookmark
                    boolean added = BookmarkManager.getInstance().toggleBookmark(stack);
                    DexSoundHelper.playButtonClick(1.2F);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                                Component.literal("DEX: " + (added ? "Pinned " : "Unpinned ") + stack.getHoverName().getString() + " from Bookmarks!")
                                        .withStyle(added ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                                true
                        );
                    }
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (!overlayActive) return;

        if (itemGrid.charTyped(event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        List<Component> tooltip = event.getToolTip();
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String namespace = key.getNamespace();
        ModInfo modInfo = ItemCatalogManager.getInstance().getModInfo(namespace);
        String modDisplay = modInfo != null ? modInfo.getDisplayName() : namespace;

        // Mod origin indicator
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Origin: ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(modDisplay).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC)));

        // Recipe and usage key hints
        boolean hasRecipes = RecipeIndexManager.getInstance().hasRecipes(stack);
        boolean hasUsages = RecipeIndexManager.getInstance().hasUsages(stack);
        if (hasRecipes || hasUsages) {
            Component hint = Component.literal("[ ")
                    .append(Component.literal("R").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(": Recipes | ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("U").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(": Usages | ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("A").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(": Pin ]").withStyle(ChatFormatting.GRAY));
            tooltip.add(hint);
        }
    }
}
