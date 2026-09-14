package com.dex.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.*;
import java.util.function.Consumer;

/**
 * Mock implementations of JEI registration interfaces that capture categories,
 * catalysts (workstations), and recipes from third-party JEI plugins.
 */
public class MockJeiRegistrations {

    public static class CategoryRegistration implements IRecipeCategoryRegistration {
        private final List<IRecipeCategory<?>> categories = new ArrayList<>();
        private final IJeiHelpers jeiHelpers = JeiProxyHelper.createJeiHelpers();

        @Override
        public IJeiHelpers getJeiHelpers() {
            return jeiHelpers;
        }

        @Override
        public void addRecipeCategories(IRecipeCategory<?>... recipeCategories) {
            if (recipeCategories != null) {
                for (IRecipeCategory<?> cat : recipeCategories) {
                    if (cat != null && !categories.contains(cat)) {
                        categories.add(cat);
                    }
                }
            }
        }

        public List<IRecipeCategory<?>> getCategories() {
            return Collections.unmodifiableList(categories);
        }
    }

    public static class CatalystRegistration implements IRecipeCatalystRegistration {
        private final Map<RecipeType<?>, List<ItemStack>> catalysts = new HashMap<>();
        private final IJeiHelpers jeiHelpers = JeiProxyHelper.createJeiHelpers();
        private final IIngredientManager ingredientManager = JeiProxyHelper.createIngredientManager();

        @Override
        public IIngredientManager getIngredientManager() {
            return ingredientManager;
        }

        @Override
        public IJeiHelpers getJeiHelpers() {
            return jeiHelpers;
        }

        @Override
        public void addRecipeCatalysts(RecipeType<?> recipeType, ItemLike... ingredients) {
            if (recipeType == null || ingredients == null) return;
            for (ItemLike itemLike : ingredients) {
                if (itemLike != null) {
                    catalysts.computeIfAbsent(recipeType, k -> new ArrayList<>())
                            .add(itemLike.asItem().getDefaultInstance());
                }
            }
        }

        @Override
        public void addRecipeCatalysts(RecipeType<?> recipeType, ItemStack... ingredients) {
            if (recipeType == null || ingredients == null) return;
            for (ItemStack stack : ingredients) {
                if (stack != null && !stack.isEmpty()) {
                    catalysts.computeIfAbsent(recipeType, k -> new ArrayList<>()).add(stack.copy());
                }
            }
        }

        @Override
        public void addRecipeCatalyst(RecipeType<?> recipeType, Consumer<IIngredientAcceptor<?>> ingredientAdder) {
            // Optional functional catalyst adder
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> void addRecipeCatalysts(RecipeType<?> recipeType, IIngredientType<T> ingredientType, List<T> ingredients) {
            if (recipeType == null || ingredients == null) return;
            if (VanillaTypes.ITEM_STACK.equals(ingredientType)) {
                for (T obj : ingredients) {
                    if (obj instanceof ItemStack stack && !stack.isEmpty()) {
                        catalysts.computeIfAbsent(recipeType, k -> new ArrayList<>()).add(stack.copy());
                    }
                }
            }
        }

        @Override
        public void addRecipeCatalyst(ItemStack itemStack, RecipeType<?>... recipeTypes) {
            if (itemStack == null || itemStack.isEmpty() || recipeTypes == null) return;
            for (RecipeType<?> type : recipeTypes) {
                if (type != null) {
                    catalysts.computeIfAbsent(type, k -> new ArrayList<>()).add(itemStack.copy());
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> void addRecipeCatalyst(IIngredientType<T> ingredientType, T ingredient, RecipeType<?>... recipeTypes) {
            if (ingredient == null || recipeTypes == null) return;
            if (VanillaTypes.ITEM_STACK.equals(ingredientType) && ingredient instanceof ItemStack stack) {
                addRecipeCatalyst(stack, recipeTypes);
            }
        }

        public Map<RecipeType<?>, List<ItemStack>> getCatalysts() {
            return Collections.unmodifiableMap(catalysts);
        }
    }

    public static class RecipeRegistration implements IRecipeRegistration {
        private final Map<RecipeType<?>, List<Object>> recipes = new HashMap<>();
        private final IJeiHelpers jeiHelpers = JeiProxyHelper.createJeiHelpers();
        private final IIngredientManager ingredientManager = JeiProxyHelper.createIngredientManager();
        private final IVanillaRecipeFactory vanillaRecipeFactory = (IVanillaRecipeFactory) JeiProxyHelper.createProxy(
                IVanillaRecipeFactory.class,
                (proxy, method, args) -> JeiProxyHelper.getDefaultReturnValue(method.getReturnType())
        );

        @Override
        public IJeiHelpers getJeiHelpers() {
            return jeiHelpers;
        }

        @Override
        public IIngredientManager getIngredientManager() {
            return ingredientManager;
        }

        @Override
        public IVanillaRecipeFactory getVanillaRecipeFactory() {
            return vanillaRecipeFactory;
        }

        @Override
        public <T> void addRecipes(RecipeType<T> recipeType, List<T> recipes) {
            if (recipeType != null && recipes != null && !recipes.isEmpty()) {
                this.recipes.computeIfAbsent(recipeType, k -> new ArrayList<>()).addAll(recipes);
            }
        }

        @Override
        public <T> void addIngredientInfo(T ingredient, IIngredientType<T> ingredientType, Component... descriptionComponents) {
        }

        @Override
        public <T> void addIngredientInfo(List<T> ingredients, IIngredientType<T> ingredientType, Component... descriptionComponents) {
        }

        public Map<RecipeType<?>, List<Object>> getRecipes() {
            return Collections.unmodifiableMap(recipes);
        }
    }
}
