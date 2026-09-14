package com.dex.compat.jei;

import com.dex.api.IDexRecipeCategory;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

/**
 * Adapts a JEI IRecipeCategory<T> into a DEX IDexRecipeCategory<T>.
 */
public class JeiCategoryAdapter<T> implements IDexRecipeCategory<T> {
    private final IRecipeCategory<T> jeiCategory;
    private final ItemStack iconStack;
    private final IRecipeSlotsView emptySlotsView = (IRecipeSlotsView) Proxy.newProxyInstance(
            IRecipeSlotsView.class.getClassLoader(),
            new Class<?>[]{IRecipeSlotsView.class},
            (proxy, method, args) -> {
                if (List.class.isAssignableFrom(method.getReturnType())) return Collections.emptyList();
                return JeiProxyHelper.getDefaultReturnValue(method.getReturnType());
            }
    );

    public JeiCategoryAdapter(IRecipeCategory<T> jeiCategory, ItemStack iconStack) {
        this.jeiCategory = jeiCategory;
        this.iconStack = iconStack != null ? iconStack : ItemStack.EMPTY;
    }

    public IRecipeCategory<T> getJeiCategory() {
        return jeiCategory;
    }

    @Override
    public ResourceLocation getId() {
        return jeiCategory.getRecipeType().getUid();
    }

    @Override
    public Component getTitle() {
        return jeiCategory.getTitle();
    }

    @Override
    public ItemStack getIcon() {
        return iconStack;
    }

    @Override
    public int getDisplayWidth() {
        return Math.max(120, jeiCategory.getWidth());
    }

    @Override
    public int getDisplayHeight() {
        return Math.max(80, jeiCategory.getHeight());
    }

    /**
     * Extracts all input, output, and catalyst slots for a specific recipe using the category's layout logic.
     */
    public List<MockRecipeLayoutBuilder.CapturedSlot> buildSlots(T recipe) {
        MockRecipeLayoutBuilder builder = new MockRecipeLayoutBuilder();
        try {
            jeiCategory.setRecipe(builder, recipe, (IFocusGroup) Proxy.newProxyInstance(
                    IFocusGroup.class.getClassLoader(),
                    new Class<?>[]{IFocusGroup.class},
                    (proxy, method, args) -> {
                        if ("isEmpty".equals(method.getName())) return true;
                        if (List.class.isAssignableFrom(method.getReturnType())) return Collections.emptyList();
                        return JeiProxyHelper.getDefaultReturnValue(method.getReturnType());
                    }
            ));
        } catch (Throwable t) {
            // Ignore layout errors for incompatible custom recipes
        }
        return builder.getSlots();
    }

    @Override
    public void draw(T recipe, GuiGraphics graphics, double mouseX, double mouseY) {
        try {
            jeiCategory.draw(recipe, emptySlotsView, graphics, mouseX, mouseY);
        } catch (Throwable ignored) {
        }
    }
}
