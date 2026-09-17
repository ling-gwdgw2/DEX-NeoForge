package com.dex.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal helper for reliably extracting ingredients and categorizing recipes across Minecraft and all modded recipe types.
 */
public final class RecipeIngredientHelper {

    private static final Map<Class<?>, List<Field>> CACHED_INGREDIENT_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Method>> CACHED_INGREDIENT_METHODS = new ConcurrentHashMap<>();

    private RecipeIngredientHelper() {}

    /**
     * Extracts all ingredients from any Recipe instance.
     * Guarantees retrieval for Smithing recipes (which return empty NonNullList in 1.21.1)
     * and modded recipes (such as Cataclysm WeaponfusionRecipe) that do not override getIngredients().
     */
    public static List<Ingredient> getIngredients(Recipe<?> recipe) {
        if (recipe == null) return Collections.emptyList();

        // 1. Try standard getIngredients()
        try {
            NonNullList<Ingredient> standard = recipe.getIngredients();
            if (standard != null && !standard.isEmpty()) {
                boolean hasValid = false;
                for (Ingredient ing : standard) {
                    if (ing != null && !ing.isEmpty()) {
                        hasValid = true;
                        break;
                    }
                }
                if (hasValid) {
                    List<Ingredient> result = new ArrayList<>(standard.size());
                    for (Ingredient ing : standard) {
                        if (ing != null && !ing.isEmpty()) {
                            result.add(ing);
                        }
                    }
                    if (!result.isEmpty()) return result;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Specific fast-path for SmithingTransformRecipe
        if (recipe instanceof SmithingTransformRecipe transform) {
            List<Ingredient> list = extractFieldsDirectly(transform, "template", "base", "addition");
            if (!list.isEmpty()) return list;
        }

        // 3. Specific fast-path for SmithingTrimRecipe
        if (recipe instanceof SmithingTrimRecipe trim) {
            List<Ingredient> list = extractFieldsDirectly(trim, "template", "base", "addition");
            if (!list.isEmpty()) return list;
        }

        // 4. Cached reflection inspection for any other recipe
        return findIngredientsViaReflection(recipe);
    }

    private static List<Ingredient> extractFieldsDirectly(Object target, String... fieldNames) {
        List<Ingredient> list = new ArrayList<>(fieldNames.length);
        Class<?> clazz = target.getClass();
        for (String name : fieldNames) {
            try {
                Field f = getField(clazz, name);
                if (f != null) {
                    f.setAccessible(true);
                    Object val = f.get(target);
                    if (val instanceof Ingredient ing && !ing.isEmpty()) {
                        list.add(ing);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return list;
    }

    private static Field getField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static List<Ingredient> findIngredientsViaReflection(Recipe<?> recipe) {
        Class<?> clazz = recipe.getClass();
        List<Field> fields = CACHED_INGREDIENT_FIELDS.computeIfAbsent(clazz, c -> {
            List<Field> detected = new ArrayList<>();
            Class<?> curr = c;
            while (curr != null && curr != Object.class) {
                for (Field f : curr.getDeclaredFields()) {
                    Class<?> type = f.getType();
                    if (Ingredient.class.isAssignableFrom(type)
                            || Optional.class.isAssignableFrom(type)
                            || Ingredient[].class.isAssignableFrom(type)
                            || Collection.class.isAssignableFrom(type)) {
                        f.setAccessible(true);
                        detected.add(f);
                    }
                }
                curr = curr.getSuperclass();
            }
            return detected;
        });

        List<Ingredient> ingredients = new ArrayList<>();

        for (Field f : fields) {
            try {
                Object val = f.get(recipe);
                if (val == null) continue;

                if (val instanceof Ingredient ing) {
                    if (!ing.isEmpty()) ingredients.add(ing);
                } else if (val instanceof Optional<?> opt) {
                    if (opt.isPresent() && opt.get() instanceof Ingredient ing && !ing.isEmpty()) {
                        ingredients.add(ing);
                    }
                } else if (val instanceof Ingredient[] arr) {
                    for (Ingredient ing : arr) {
                        if (ing != null && !ing.isEmpty()) ingredients.add(ing);
                    }
                } else if (val instanceof Collection<?> col) {
                    for (Object elem : col) {
                        if (elem instanceof Ingredient ing && !ing.isEmpty()) {
                            ingredients.add(ing);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (!ingredients.isEmpty()) return ingredients;

        // Fallback: check getter methods like getbaseIngredient(), getAdditionIngredient()
        List<Method> methods = CACHED_INGREDIENT_METHODS.computeIfAbsent(clazz, c -> {
            List<Method> detected = new ArrayList<>();
            for (Method m : c.getMethods()) {
                if (m.getParameterCount() == 0 && Ingredient.class.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    detected.add(m);
                }
            }
            return detected;
        });

        for (Method m : methods) {
            try {
                Object res = m.invoke(recipe);
                if (res instanceof Ingredient ing && !ing.isEmpty()) {
                    ingredients.add(ing);
                }
            } catch (Throwable ignored) {}
        }

        return ingredients;
    }

    /**
     * Categorizes a recipe into a clean category representation for tab grouping.
     */
    public record RecipeCategoryInfo(
            String id,
            Component title,
            ItemStack icon,
            WorkstationKind kind
    ) {}

    public enum WorkstationKind {
        CRAFTING_TABLE,
        FURNACE,
        SMITHING_TABLE,
        STONECUTTER,
        FUSION_ANVIL,
        GENERIC
    }

    public static RecipeCategoryInfo getCategoryInfo(RecipeHolder<?> holder) {
        if (holder == null || holder.value() == null) {
            return new RecipeCategoryInfo("crafting", Component.literal("Crafting"), new ItemStack(Items.CRAFTING_TABLE), WorkstationKind.CRAFTING_TABLE);
        }

        Recipe<?> recipe = holder.value();
        ResourceLocation id = holder.id();
        String path = id.getPath().toLowerCase(Locale.ROOT);

        // 1. Crafting
        if (recipe instanceof CraftingRecipe) {
            return new RecipeCategoryInfo("crafting", Component.literal("Crafting"), new ItemStack(Items.CRAFTING_TABLE), WorkstationKind.CRAFTING_TABLE);
        }

        // 2. Cooking / Furnace
        if (recipe instanceof SmeltingRecipe) {
            return new RecipeCategoryInfo("smelting", Component.literal("Smelting"), new ItemStack(Blocks.FURNACE), WorkstationKind.FURNACE);
        }
        if (recipe instanceof BlastingRecipe) {
            return new RecipeCategoryInfo("blasting", Component.literal("Blasting"), new ItemStack(Blocks.BLAST_FURNACE), WorkstationKind.FURNACE);
        }
        if (recipe instanceof SmokingRecipe) {
            return new RecipeCategoryInfo("smoking", Component.literal("Smoking"), new ItemStack(Blocks.SMOKER), WorkstationKind.FURNACE);
        }
        if (recipe instanceof CampfireCookingRecipe) {
            return new RecipeCategoryInfo("campfire", Component.literal("Campfire"), new ItemStack(Blocks.CAMPFIRE), WorkstationKind.FURNACE);
        }
        if (recipe instanceof AbstractCookingRecipe) {
            return new RecipeCategoryInfo("cooking", Component.literal("Cooking"), new ItemStack(Blocks.FURNACE), WorkstationKind.FURNACE);
        }

        // 3. Smithing Table
        if (recipe instanceof SmithingRecipe
                || recipe.getType() == RecipeType.SMITHING
                || (recipe.getSerializer() == RecipeSerializer.SMITHING_TRANSFORM || recipe.getSerializer() == RecipeSerializer.SMITHING_TRIM)
                || path.startsWith("smithing/")
                || path.contains("smithing")) {
            // Check if it's weapon_fusion from cataclysm
            if (path.contains("fusion") || recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("fusion")) {
                return new RecipeCategoryInfo("weapon_fusion", Component.literal("Weapon Fusion"), new ItemStack(Blocks.ANVIL), WorkstationKind.FUSION_ANVIL);
            }
            return new RecipeCategoryInfo("smithing", Component.literal("Smithing"), new ItemStack(Blocks.SMITHING_TABLE), WorkstationKind.SMITHING_TABLE);
        }

        // 4. Stonecutter
        if (recipe instanceof StonecutterRecipe || recipe.getType() == RecipeType.STONECUTTING) {
            return new RecipeCategoryInfo("stonecutting", Component.literal("Stonecutting"), new ItemStack(Blocks.STONECUTTER), WorkstationKind.STONECUTTER);
        }

        // 5. Special Modded Types
        if (path.contains("fusion") || recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("fusion")) {
            return new RecipeCategoryInfo("weapon_fusion", Component.literal("Weapon Fusion"), new ItemStack(Blocks.ANVIL), WorkstationKind.FUSION_ANVIL);
        }

        // 6. Registered RecipeType Fallback
        try {
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (typeId != null && !typeId.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"))) {
                String typePath = typeId.getPath();
                String displayName = formatTitle(typePath);
                ItemStack icon = guessIcon(typePath);
                WorkstationKind kind = typePath.contains("anvil") ? WorkstationKind.FUSION_ANVIL : WorkstationKind.GENERIC;
                return new RecipeCategoryInfo(typeId.toString(), Component.literal(displayName), icon, kind);
            }
        } catch (Throwable ignored) {}

        return new RecipeCategoryInfo("crafting", Component.literal("Crafting"), new ItemStack(Items.CRAFTING_TABLE), WorkstationKind.CRAFTING_TABLE);
    }

    private static String formatTitle(String raw) {
        if (raw == null || raw.isEmpty()) return "Recipe";
        String cleaned = raw.replace('_', ' ').replace('/', ' ');
        String[] words = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static ItemStack guessIcon(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("anvil") || lower.contains("fusion")) return new ItemStack(Blocks.ANVIL);
        if (lower.contains("furnace") || lower.contains("smelt")) return new ItemStack(Blocks.FURNACE);
        if (lower.contains("smithing")) return new ItemStack(Blocks.SMITHING_TABLE);
        if (lower.contains("crush") || lower.contains("press") || lower.contains("mill")) return new ItemStack(Blocks.PISTON);
        if (lower.contains("brew") || lower.contains("potion")) return new ItemStack(Blocks.BREWING_STAND);
        if (lower.contains("saw") || lower.contains("cut")) return new ItemStack(Blocks.STONECUTTER);
        if (lower.contains("enchant") || lower.contains("altar") || lower.contains("infus")) return new ItemStack(Blocks.ENCHANTING_TABLE);
        return new ItemStack(Items.CRAFTING_TABLE);
    }
}
