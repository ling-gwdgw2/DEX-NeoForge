package com.dex.compat.jei;

import com.dex.catalog.ItemCatalogManager;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Creates resilient dynamic proxies for JEI helper interfaces (IJeiHelpers, IGuiHelper, etc.).
 * Ensures third-party JEI plugins can safely query GUI helpers and drawables without crashing.
 */
public class JeiProxyHelper {
    private static final IDrawable EMPTY_DRAWABLE = new IDrawable() {
        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }

        @Override
        public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
        }
    };

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> interfaceClass, InvocationHandler handler) {
        ClassLoader cl = interfaceClass.getClassLoader() != null ? interfaceClass.getClassLoader() : Thread.currentThread().getContextClassLoader();
        return (T) Proxy.newProxyInstance(cl, new Class<?>[]{interfaceClass}, handler);
    }

    public static IJeiHelpers createJeiHelpers() {
        IGuiHelper guiHelper = createGuiHelper();
        IFocusFactory focusFactory = createFocusFactory();
        IIngredientManager ingredientManager = createIngredientManager();

        return createProxy(IJeiHelpers.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("getGuiHelper".equals(name)) return guiHelper;
            if ("getFocusFactory".equals(name)) return focusFactory;
            if ("getIngredientManager".equals(name)) return ingredientManager;
            return getDefaultReturnValue(method.getReturnType());
        });
    }

    public static IGuiHelper createGuiHelper() {
        return createProxy(IGuiHelper.class, (proxy, method, args) -> {
            Class<?> returnType = method.getReturnType();
            if (IDrawableBuilder.class.isAssignableFrom(returnType)) {
                return createDrawableBuilder();
            }
            if (IDrawableStatic.class.isAssignableFrom(returnType)) {
                return createDrawableStatic();
            }
            if (IDrawableAnimated.class.isAssignableFrom(returnType)) {
                return createDrawableAnimated();
            }
            if (IDrawable.class.isAssignableFrom(returnType)) {
                if (args != null && args.length > 1 && args[1] instanceof ItemStack stack) {
                    return createItemStackDrawable(stack);
                }
                return EMPTY_DRAWABLE;
            }
            return getDefaultReturnValue(returnType);
        });
    }

    public static IDrawableBuilder createDrawableBuilder() {
        return createProxy(IDrawableBuilder.class, (proxy, method, args) -> {
            Class<?> returnType = method.getReturnType();
            if (IDrawableBuilder.class.isAssignableFrom(returnType)) {
                return proxy;
            }
            if (IDrawableStatic.class.isAssignableFrom(returnType)) {
                return createDrawableStatic();
            }
            if (IDrawableAnimated.class.isAssignableFrom(returnType)) {
                return createDrawableAnimated();
            }
            return getDefaultReturnValue(returnType);
        });
    }

    public static IDrawableStatic createDrawableStatic() {
        return createProxy(IDrawableStatic.class, (proxy, method, args) -> {
            if ("getWidth".equals(method.getName())) return 16;
            if ("getHeight".equals(method.getName())) return 16;
            return getDefaultReturnValue(method.getReturnType());
        });
    }

    public static IDrawableAnimated createDrawableAnimated() {
        return createProxy(IDrawableAnimated.class, (proxy, method, args) -> {
            if ("getWidth".equals(method.getName())) return 16;
            if ("getHeight".equals(method.getName())) return 16;
            return getDefaultReturnValue(method.getReturnType());
        });
    }

    public static IDrawable createItemStackDrawable(ItemStack stack) {
        return new IDrawable() {
            @Override
            public int getWidth() {
                return 16;
            }

            @Override
            public int getHeight() {
                return 16;
            }

            @Override
            public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
                if (!stack.isEmpty()) {
                    guiGraphics.renderItem(stack, xOffset, yOffset);
                }
            }
        };
    }

    public static IFocusFactory createFocusFactory() {
        return createProxy(IFocusFactory.class, (proxy, method, args) -> {
            Class<?> returnType = method.getReturnType();
            if (IFocusGroup.class.isAssignableFrom(returnType)) {
                return createProxy(IFocusGroup.class, (p, m, a) -> {
                    if ("isEmpty".equals(m.getName())) return true;
                    if (List.class.isAssignableFrom(m.getReturnType())) return Collections.emptyList();
                    return getDefaultReturnValue(m.getReturnType());
                });
            }
            return getDefaultReturnValue(returnType);
        });
    }

    public static IIngredientManager createIngredientManager() {
        return createProxy(IIngredientManager.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("getAllIngredients".equals(name) && args != null && args.length > 0) {
                if (VanillaTypes.ITEM_STACK.equals(args[0])) {
                    return ItemCatalogManager.getInstance().getAllItems();
                }
            }
            Class<?> returnType = method.getReturnType();
            if (List.class.isAssignableFrom(returnType)) return Collections.emptyList();
            if (Optional.class.isAssignableFrom(returnType)) return Optional.empty();
            return getDefaultReturnValue(returnType);
        });
    }

    public static Object getDefaultReturnValue(Class<?> type) {
        if (type.equals(boolean.class) || type.equals(Boolean.class)) return false;
        if (type.equals(int.class) || type.equals(Integer.class)) return 0;
        if (type.equals(long.class) || type.equals(Long.class)) return 0L;
        if (type.equals(float.class) || type.equals(Float.class)) return 0.0f;
        if (type.equals(double.class) || type.equals(Double.class)) return 0.0d;
        if (type.equals(List.class)) return Collections.emptyList();
        if (type.equals(Optional.class)) return Optional.empty();
        return null;
    }
}
