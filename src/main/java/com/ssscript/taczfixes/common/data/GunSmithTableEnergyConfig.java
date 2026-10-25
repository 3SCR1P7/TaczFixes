package com.ssscript.taczfixes.common.data;

import org.jetbrains.annotations.Nullable;

public final class GunSmithTableEnergyConfig {
    private GunSmithTableEnergyConfig() {
    }

    public static final class Block {
        public Integer power_max;
        public Integer consumption;
        public Double time_base;
        public Double time_per_type;
        public Double time_per_item;
        public Double cooldown;
    }

    public static final class Recipe {
        public Integer consumption_manually;
        public Integer consumption_auto;
        public Double crafting_time;
    }

    public interface BlockDataAccess {
        @Nullable
        Block taczfixes$getEnergyConfig();
    }

    public interface TableRecipeAccess {
        @Nullable
        Recipe taczfixes$getEnergyConfig();
    }

    public interface RecipeAccess {
        @Nullable
        Recipe taczfixes$getEnergyConfig();
    }
}
