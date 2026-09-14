package com.dex.client;

import com.dex.DEXMod;
import com.dex.catalog.ItemCatalogManager;
import com.dex.client.gui.overlay.ItemGridOverlay;
import com.dex.client.gui.overlay.ModSidebarWidget;
import com.dex.plugin.DexPluginManager;
import com.dex.recipe.RecipeIndexManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

public class DEXClientEvents {
    private static final ModSidebarWidget modSidebar = new ModSidebarWidget();
    private static final ItemGridOverlay itemGrid = new ItemGridOverlay();
    private static boolean overlayActive = false;
    private static boolean overlayVisible = true;

    private static int lastContainerRight = -1;
    private static int lastScreenWidth = -1;
    private static int lastScreenHeight = -1;

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
        if (!overlayActive) return;

        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        float partialTick = event.getPartialTick();

        // 1. Render Mod Sidebar (Left of grid)
        modSidebar.render(event.getGuiGraphics(), mouseX, mouseY, partialTick);

        // 2. Render Item Grid
        itemGrid.render(event.getGuiGraphics(), mouseX, mouseY, partialTick);

        // 3. Render Tooltips on top
        itemGrid.renderTooltips(event.getGuiGraphics(), mouseX, mouseY);
        modSidebar.renderTooltips(event.getGuiGraphics(), mouseX, mouseY);
    }

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!overlayActive) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        int button = event.getButton();

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
        if (!overlayActive) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        double scrollX = event.getScrollDeltaX();
        double scrollY = event.getScrollDeltaY();

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
            event.setCanceled(true);
            return;
        }

        if (!overlayActive) return;

        if (itemGrid.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (!overlayActive) return;

        if (itemGrid.charTyped(event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }
}
