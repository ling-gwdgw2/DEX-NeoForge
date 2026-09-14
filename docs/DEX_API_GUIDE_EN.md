# DEX API Guide for Third-Party Developers

This guide explains how to integrate your Minecraft mod with **DEX** (NeoForge 1.21.1) to register custom machines, workstations, and recipe categories.

---

## 1. Adding the Dependency to `build.gradle`

```groovy
repositories {
    // Configure maven repository or flatDir pointing to DEX jar
    flatDir {
        dir 'libs'
    }
}

dependencies {
    // Reference DEX API
    compileOnly "com.dex:dex:1.0.0+1.21.1"
}
```

---

## 2. Creating a Plugin Class with `@DexPlugin`

Create a class that implements `IDexPlugin` and annotate it with `@DexPlugin`. DEX will automatically discover and load it via NeoForge ASM scan data:

```java
package com.example.mymod.compat.dex;

import com.dex.api.*;
import com.example.mymod.init.ModBlocks;
import com.example.mymod.recipe.CrusherRecipe;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@DexPlugin
public class MyModDexPlugin implements IDexPlugin {
    public static final ResourceLocation CRUSHER_CATEGORY = ResourceLocation.fromNamespaceAndPath("mymod", "crushing");

    @Override
    public void registerCategories(IDexCategoryRegistry registry) {
        // 1. Register a new recipe category (Tab title and icon)
        registry.registerCategory(new IDexRecipeCategory<CrusherRecipe>() {
            @Override
            public ResourceLocation getId() {
                return CRUSHER_CATEGORY;
            }

            @Override
            public Component getTitle() {
                return Component.literal("Ore Crusher");
            }

            @Override
            public ItemStack getIcon() {
                return new ItemStack(ModBlocks.CRUSHER.get());
            }

            @Override
            public int getDisplayWidth() {
                return 160;
            }

            @Override
            public int getDisplayHeight() {
                return 120;
            }
        });
    }

    @Override
    public void registerWorkstations(IDexWorkstationRegistry registry) {
        // 2. Associate the workstation block with this recipe category
        registry.registerWorkstation(CRUSHER_CATEGORY, new ItemStack(ModBlocks.CRUSHER.get()));
    }

    @Override
    public void registerRecipes(IDexRecipeRegistry registry, ClientLevel level) {
        // 3. Supply all recipes for this category into DEX
        registry.registerRecipes(CRUSHER_CATEGORY, MyModRecipes.getAllCrusherRecipes(level));
    }
}
```

---

## 3. Core DEX API Interfaces

| Interface | Description |
|---|---|
| `@DexPlugin` | Class-level annotation discovered automatically by NeoForge ASM scanning. |
| `IDexPlugin` | Main plugin contract with 3 lifecycle methods (`registerCategories`, `registerWorkstations`, `registerRecipes`). |
| `IDexRecipeCategory<T>` | Defines recipe category specifications: unique ID, title, icon, and display dimensions. |
| `IDexWorkstationRegistry` | Maps Category IDs to workstation block/item stacks. |
| `IDexRecipeRegistry` | Stores custom recipes indexed by Category ID for display in the viewer. |
