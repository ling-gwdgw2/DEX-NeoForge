package com.dex.recipe.trading;

import net.minecraft.world.item.ItemStack;

public class VillagerTradeEntry {
    private final String profession;
    private final int level;
    private final ItemStack costA;
    private final ItemStack costB;
    private final ItemStack result;

    public VillagerTradeEntry(String profession, int level, ItemStack costA, ItemStack costB, ItemStack result) {
        this.profession = profession;
        this.level = level;
        this.costA = costA.copy();
        this.costB = costB != null ? costB.copy() : ItemStack.EMPTY;
        this.result = result.copy();
    }

    public String getProfession() {
        return profession;
    }

    public int getLevel() {
        return level;
    }

    public String getLevelName() {
        return switch (level) {
            case 1 -> "Novice";
            case 2 -> "Apprentice";
            case 3 -> "Journeyman";
            case 4 -> "Expert";
            case 5 -> "Master";
            default -> "Level " + level;
        };
    }

    public ItemStack getCostA() {
        return costA;
    }

    public ItemStack getCostB() {
        return costB;
    }

    public ItemStack getResult() {
        return result;
    }
}
