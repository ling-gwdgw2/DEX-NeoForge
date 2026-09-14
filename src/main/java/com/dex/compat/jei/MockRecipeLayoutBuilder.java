package com.dex.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Mock implementation of JEI's IRecipeLayoutBuilder.
 * Captures slot positions and ingredients defined by third-party JEI recipe categories.
 */
public class MockRecipeLayoutBuilder implements IRecipeLayoutBuilder {
    public static class CapturedSlot {
        public final RecipeIngredientRole role;
        public int x = 0;
        public int y = 0;
        public final List<ItemStack> itemStacks = new ArrayList<>();

        public CapturedSlot(RecipeIngredientRole role) {
            this.role = role;
        }
    }

    private final List<CapturedSlot> slots = new ArrayList<>();

    public List<CapturedSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    @Override
    public IRecipeSlotBuilder addSlot(RecipeIngredientRole role) {
        CapturedSlot slot = new CapturedSlot(role);
        slots.add(slot);
        return createSlotProxy(slot);
    }

    @Override
    public IRecipeSlotBuilder addSlot(RecipeIngredientRole role, int x, int y) {
        CapturedSlot slot = new CapturedSlot(role);
        slot.x = x;
        slot.y = y;
        slots.add(slot);
        return createSlotProxy(slot);
    }

    @Override
    @SuppressWarnings("removal")
    public IRecipeSlotBuilder addSlotToWidget(RecipeIngredientRole role, mezz.jei.api.gui.widgets.ISlottedWidgetFactory<?> widgetFactory) {
        return addSlot(role);
    }

    @Override
    public IIngredientAcceptor<?> addInvisibleIngredients(RecipeIngredientRole recipeIngredientRole) {
        CapturedSlot slot = new CapturedSlot(recipeIngredientRole);
        return createSlotProxy(slot);
    }

    @Override
    public void setShapeless() {
    }

    @Override
    public void setShapeless(int posX, int posY) {
    }

    @Override
    public void moveRecipeTransferButton(int posX, int posY) {
    }

    @Override
    public void createFocusLink(IIngredientAcceptor<?>... slots) {
    }

    private IRecipeSlotBuilder createSlotProxy(CapturedSlot slot) {
        return (IRecipeSlotBuilder) Proxy.newProxyInstance(
                IRecipeSlotBuilder.class.getClassLoader(),
                new Class<?>[]{IRecipeSlotBuilder.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setPosition".equals(name) && args != null && args.length >= 2) {
                        if (args[0] instanceof Number nx && args[1] instanceof Number ny) {
                            slot.x = nx.intValue();
                            slot.y = ny.intValue();
                        }
                        return proxy;
                    }
                    if ("addItemStack".equals(name) && args != null && args.length >= 1) {
                        if (args[0] instanceof ItemStack stack && !stack.isEmpty()) {
                            slot.itemStacks.add(stack.copy());
                        }
                        return proxy;
                    }
                    if ("addItemStacks".equals(name) && args != null && args.length >= 1) {
                        if (args[0] instanceof List<?> list) {
                            for (Object obj : list) {
                                if (obj instanceof ItemStack stack && !stack.isEmpty()) {
                                    slot.itemStacks.add(stack.copy());
                                }
                            }
                        }
                        return proxy;
                    }
                    if ("addIngredients".equals(name) && args != null) {
                        if (args.length >= 1 && args[0] instanceof Ingredient ing) {
                            for (ItemStack st : ing.getItems()) {
                                if (st != null && !st.isEmpty()) slot.itemStacks.add(st.copy());
                            }
                        } else if (args.length >= 2 && VanillaTypes.ITEM_STACK.equals(args[0]) && args[1] instanceof List<?> list) {
                            for (Object obj : list) {
                                if (obj instanceof ItemStack stack && !stack.isEmpty()) {
                                    slot.itemStacks.add(stack.copy());
                                }
                            }
                        }
                        return proxy;
                    }
                    if ("addIngredient".equals(name) && args != null && args.length >= 2) {
                        if (VanillaTypes.ITEM_STACK.equals(args[0]) && args[1] instanceof ItemStack stack) {
                            if (!stack.isEmpty()) slot.itemStacks.add(stack.copy());
                        }
                        return proxy;
                    }
                    if ("addIngredientsUnsafe".equals(name) && args != null && args.length >= 1) {
                        if (args[0] instanceof List<?> list) {
                            for (Object obj : list) {
                                if (obj instanceof ItemStack stack && !stack.isEmpty()) {
                                    slot.itemStacks.add(stack.copy());
                                }
                            }
                        }
                        return proxy;
                    }

                    Class<?> returnType = method.getReturnType();
                    if (returnType.isAssignableFrom(IRecipeSlotBuilder.class)
                            || returnType.isAssignableFrom(IIngredientAcceptor.class)) {
                        return proxy;
                    }
                    return JeiProxyHelper.getDefaultReturnValue(returnType);
                }
        );
    }
}
