# DEX API Guide สำหรับนักพัฒนา Mod ภายนอก (Third-Party Developers)

คู่มือการเชื่อมต่อม็อดของคุณเข้ากับ **DEX** (NeoForge 1.21.1) เพื่อลงทะเบียนเตา, เครื่องจักร, หรือแท่นคราฟต์เฉพาะตัว

---

## 1. วิธีเพิ่ม Dependency ใน `build.gradle` ของ Mod คุณ

```groovy
repositories {
    // กำหนด maven repository หรือ flatDir ที่ชี้มายัง DEX jar
    flatDir {
        dir 'libs'
    }
}

dependencies {
    // อ้างอิง DEX API
    compileOnly "com.dex:dex:1.0.0+1.21.1"
}
```

---

## 2. การสร้าง Plugin Class ด้วย `@DexPlugin`

สร้างคลาสที่ implement `IDexPlugin` และใส่ Annotation `@DexPlugin` ที่หัวคลาส โดยไม่ต้องลงทะเบียนใน mod bus เพิ่มเติม DEX จะสแกนและค้นหาให้อัตโนมัติ:

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
        // 1. ลงทะเบียนหมวดหมู่สูตรใหม่ (ชื่อและไอคอนแท็บ)
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
        // 2. บอก DEX ว่าบล็อกไหนในเกมคือเครื่องจักรที่ใช้คราฟต์สูตรนี้
        registry.registerWorkstation(CRUSHER_CATEGORY, new ItemStack(ModBlocks.CRUSHER.get()));
    }

    @Override
    public void registerRecipes(IDexRecipeRegistry registry, ClientLevel level) {
        // 3. ส่งรายการสูตรทั้งหมดของเครื่องจักรนี้เข้าสู่ระบบ DEX
        registry.registerRecipes(CRUSHER_CATEGORY, MyModRecipes.getAllCrusherRecipes(level));
    }
}
```

---

## 3. สรุปความสามารถของ DEX API

| Interface | หน้าที่หลัก |
|---|---|
| `@DexPlugin` | ใช้มาร์ก Class เพื่อให้ระบบ NeoForge ASM Discovery สแกนเจออัตโนมัติ |
| `IDexPlugin` | Entry point หลักของ Plugin (มี 3 วงจร: หมวดหมู่, เครื่องจักร, รายการสูตร) |
| `IDexRecipeCategory<T>` | กำหนดสเปกของหมวดหมู่: ID, ชื่อแท็บ, ไอคอนเครื่องจักร, ขนาดพื้นที่วาด |
| `IDexWorkstationRegistry` | จับคู่ระหว่าง Category ID กับไอเทมบล็อกเครื่องจักร |
| `IDexRecipeRegistry` | จัดเก็บสูตรของหมวดหมู่นั้นๆ เพื่อให้ DEX ดึงไปแสดงผล |
