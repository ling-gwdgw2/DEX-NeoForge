package com.dex.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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
                    // Also extract tool ingredient if present in modded recipes (e.g. Farmer's Delight CuttingBoardRecipe)
                    extractToolIfPresent(recipe, result);
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
        List<Ingredient> reflected = findIngredientsViaReflection(recipe);
        extractToolIfPresent(recipe, reflected);
        return reflected;
    }

    private static void extractToolIfPresent(Recipe<?> recipe, List<Ingredient> result) {
        if (recipe == null) return;
        Class<?> clazz = recipe.getClass();
        try {
            Method m = clazz.getMethod("getTool");
            Object val = m.invoke(recipe);
            if (val instanceof Ingredient toolIng && !toolIng.isEmpty() && !result.contains(toolIng)) {
                result.add(toolIng);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            Field f = getField(clazz, "tool");
            if (f != null) {
                f.setAccessible(true);
                Object val = f.get(recipe);
                if (val instanceof Ingredient toolIng && !toolIng.isEmpty() && !result.contains(toolIng)) {
                    result.add(toolIng);
                }
            }
        } catch (Throwable ignored) {}
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
                    for (Object obj : col) {
                        if (obj instanceof Ingredient ing && !ing.isEmpty()) {
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

        // 4. Stonecutter (ONLY for actual vanilla Stonecutter recipe types)
        if (recipe.getType() == RecipeType.STONECUTTING) {
            return new RecipeCategoryInfo("stonecutting", Component.literal("Stonecutting"), new ItemStack(Blocks.STONECUTTER), WorkstationKind.STONECUTTER);
        }

        // 5. Special Modded Types
        if (path.contains("fusion") || recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("fusion")) {
            return new RecipeCategoryInfo("weapon_fusion", Component.literal("Weapon Fusion"), new ItemStack(Blocks.ANVIL), WorkstationKind.FUSION_ANVIL);
        }

        // 6. Registered RecipeType Fallback (Intelligent multi-tier resolution)
        try {
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (typeId != null && !typeId.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"))) {
                WorkstationResolution res = resolveWorkstationInfo(typeId, recipe, id);
                return new RecipeCategoryInfo(res.categoryId(), Component.literal(res.title()), res.icon(), res.kind());
            }
        } catch (Throwable ignored) {}

        return new RecipeCategoryInfo("crafting", Component.literal("Crafting"), new ItemStack(Items.CRAFTING_TABLE), WorkstationKind.CRAFTING_TABLE);
    }

    public record WorkstationResolution(String categoryId, String title, ItemStack icon, WorkstationKind kind) {}

    public static WorkstationResolution resolveWorkstationInfo(ResourceLocation typeId, Recipe<?> recipe, ResourceLocation recipeId) {
        String namespace = typeId.getNamespace();
        String typePath = typeId.getPath().toLowerCase(Locale.ROOT);
        String recipePath = recipeId != null ? recipeId.getPath().toLowerCase(Locale.ROOT) : "";

        // 1. Check if DexRegistries / JEI imported catalysts has this type
        try {
            List<ItemStack> catalysts = com.dex.plugin.DexPluginManager.getInstance().getRegistries().getWorkstations(typeId);
            if (catalysts != null && !catalysts.isEmpty()) {
                ItemStack cat = catalysts.get(0);
                if (cat != null && !cat.isEmpty()) {
                    String title = cat.getHoverName().getString();
                    return new WorkstationResolution(typeId.toString(), title, cat.copy(), WorkstationKind.GENERIC);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Farmer's Delight specific detection (Cutting Board & Cooking Pot)
        if ("farmersdelight".equals(namespace) || recipe.getClass().getName().contains("farmersdelight") || recipePath.contains("salvaging")) {
            if (typePath.contains("cut") || recipePath.contains("salvaging") || recipe.getClass().getSimpleName().contains("Cutting")) {
                Item cuttingBoard = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "cutting_board"));
                ItemStack icon = (cuttingBoard != null && cuttingBoard != Items.AIR) ?
                        new ItemStack(cuttingBoard) : new ItemStack(Blocks.OAK_PLANKS);
                return new WorkstationResolution("farmersdelight:cutting", "Cutting Board", icon, WorkstationKind.GENERIC);
            }
            if (typePath.contains("cook") || recipe.getClass().getSimpleName().contains("Cooking")) {
                Item pot = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "cooking_pot"));
                ItemStack icon = (pot != null && pot != Items.AIR) ?
                        new ItemStack(pot) : new ItemStack(Blocks.CAULDRON);
                return new WorkstationResolution("farmersdelight:cooking", "Cooking Pot", icon, WorkstationKind.GENERIC);
            }
        }

        // 3. Check exact Item with same ID in BuiltInRegistries.ITEM
        // e.g. "corail_woodcutter:woodcutter" for "corail_woodcutter:woodcutting"
        Item directItem = BuiltInRegistries.ITEM.get(typeId);
        if (directItem != null && directItem != Items.AIR) {
            return new WorkstationResolution(typeId.toString(), directItem.getName(new ItemStack(directItem)).getString(),
                    new ItemStack(directItem), WorkstationKind.GENERIC);
        }

        // 4. Check matching block/item in the same mod namespace
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation itemKey = entry.getKey().location();
            if (itemKey.getNamespace().equals(namespace)) {
                String itemPath = itemKey.getPath();
                if (typePath.contains("cut") && itemPath.contains("cutting_board")) {
                    return new WorkstationResolution(typeId.toString(), "Cutting Board", new ItemStack(entry.getValue()), WorkstationKind.GENERIC);
                }
                if ((typePath.contains("woodcut") || itemPath.contains("woodcutter")) && itemPath.contains("cutter")) {
                    return new WorkstationResolution(typeId.toString(), "Woodcutter", new ItemStack(entry.getValue()), WorkstationKind.GENERIC);
                }
                if (typePath.contains("saw") && (itemPath.contains("saw") || itemPath.contains("sawmill"))) {
                    return new WorkstationResolution(typeId.toString(), "Sawmill", new ItemStack(entry.getValue()), WorkstationKind.GENERIC);
                }
                if (typePath.contains("crush") && (itemPath.contains("crusher") || itemPath.contains("crushing_wheel") || itemPath.contains("mill"))) {
                    return new WorkstationResolution(typeId.toString(), "Crusher", new ItemStack(entry.getValue()), WorkstationKind.GENERIC);
                }
                if (typePath.contains("press") && (itemPath.contains("press") || itemPath.contains("compactor"))) {
                    return new WorkstationResolution(typeId.toString(), "Mechanical Press", new ItemStack(entry.getValue()), WorkstationKind.GENERIC);
                }
                if (typePath.contains("brew") && (itemPath.contains("brew") || itemPath.contains("kettle") || itemPath.contains("vat"))) {
                    return new WorkstationResolution(typeId.toString(), "Brewing Vat", new ItemStack(entry.getValue()), WorkstationKind.GENERIC);
                }
            }
        }

        // 5. Intelligent Fallback by keywords (DO NOT default to Stonecutter for "cut"!)
        String title = formatTitle(typePath);
        ItemStack icon;
        WorkstationKind kind = WorkstationKind.GENERIC;

        if (typePath.contains("stonecut")) {
            icon = new ItemStack(Blocks.STONECUTTER);
            title = "Stonecutting";
            kind = WorkstationKind.STONECUTTER;
        } else if (typePath.contains("woodcut")) {
            icon = new ItemStack(Items.IRON_AXE);
            title = "Woodcutting";
        } else if (typePath.contains("cut")) {
            Item knife = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "flint_knife"));
            if (knife != null && knife != Items.AIR) {
                icon = new ItemStack(knife);
            } else {
                icon = new ItemStack(Items.SHEARS);
            }
            title = "Cutting";
        } else if (typePath.contains("saw")) {
            icon = new ItemStack(Items.IRON_AXE);
            title = "Sawing";
        } else if (typePath.contains("smelt") || typePath.contains("furnace")) {
            icon = new ItemStack(Blocks.FURNACE);
            title = "Smelting";
            kind = WorkstationKind.FURNACE;
        } else if (typePath.contains("blast")) {
            icon = new ItemStack(Blocks.BLAST_FURNACE);
            title = "Blasting";
            kind = WorkstationKind.FURNACE;
        } else if (typePath.contains("smoke")) {
            icon = new ItemStack(Blocks.SMOKER);
            title = "Smoking";
            kind = WorkstationKind.FURNACE;
        } else if (typePath.contains("anvil") || typePath.contains("fusion")) {
            icon = new ItemStack(Blocks.ANVIL);
            title = "Anvil / Fusion";
            kind = WorkstationKind.FUSION_ANVIL;
        } else if (typePath.contains("smithing")) {
            icon = new ItemStack(Blocks.SMITHING_TABLE);
            title = "Smithing";
            kind = WorkstationKind.SMITHING_TABLE;
        } else if (typePath.contains("brew") || typePath.contains("potion")) {
            icon = new ItemStack(Blocks.BREWING_STAND);
            title = "Brewing";
        } else if (typePath.contains("enchant")) {
            icon = new ItemStack(Blocks.ENCHANTING_TABLE);
            title = "Enchanting";
        } else if (typePath.contains("crush") || typePath.contains("press") || typePath.contains("mill")) {
            icon = new ItemStack(Blocks.PISTON);
            title = "Processing";
        } else {
            icon = new ItemStack(Items.CRAFTING_TABLE);
        }

        return new WorkstationResolution(typeId.toString(), title, icon, kind);
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
}
