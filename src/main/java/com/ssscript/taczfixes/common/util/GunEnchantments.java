package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.config.Config;
import java.util.function.IntSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public final class GunEnchantments {
    public static final class Def {
        private final ResourceKey<Enchantment> key;
        private final IntSupplier maxLevel;
        private final int weight;
        private final int minCostBase;
        private final int minCostStep;
        private final int maxCostStep;
        private final boolean tradeable;

        private Def(String path, IntSupplier maxLevel, int weight,
                    int minCostBase, int minCostStep, int maxCostStep, boolean tradeable) {
            this.key = ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.fromNamespaceAndPath("taczfixes", path));
            this.maxLevel = maxLevel;
            this.weight = weight;
            this.minCostBase = minCostBase;
            this.minCostStep = minCostStep;
            this.maxCostStep = maxCostStep;
            this.tradeable = tradeable;
        }

        public ResourceKey<Enchantment> get() {
            return key;
        }

        public ResourceKey<Enchantment> key() {
            return key;
        }

        public int maxLevel() {
            return maxLevel.getAsInt();
        }

        public int weight() {
            return weight;
        }

        public int minCostBase() {
            return minCostBase;
        }

        public int minCostStep() {
            return minCostStep;
        }

        public int maxCostStep() {
            return maxCostStep;
        }

        public boolean tradeable() {
            return tradeable;
        }
    }

    public static final Def OVERLOAD = new Def("overload", Config.ENCH_OVERLOAD_MAX_LEVEL::get, 2, 6, 8, 6, true);
    public static final Def ANNIHILATION = new Def("annihilation", Config.ENCH_ANNIHILATION_MAX_LEVEL::get, 2, 6, 8, 6, true);
    public static final Def STABILITY = new Def("stability", Config.ENCH_STABILITY_MAX_LEVEL::get, 2, 6, 8, 6, true);
    public static final Def ANTI_GRAVITY = new Def("anti_gravity", Config.ENCH_ANTIGRAVITY_MAX_LEVEL::get, 2, 6, 8, 6, true);
    public static final Def ELECTROMAGNETIC_COIL = new Def("electromagnetic_coil", Config.ENCH_COIL_MAX_LEVEL::get, 2, 6, 8, 6, true);
    public static final Def STANDARD_AMMO = new Def("standard_ammo", Config.ENCH_STANDARD_AMMO_MAX_LEVEL::get, 2, 6, 8, 6, true);
    public static final Def NEUROTOXIN = new Def("neurotoxin", Config.ENCH_NEUROTOXIN_MAX_LEVEL::get, 2, 6, 8, 6, true);
    public static final Def CHAIN_EXPLOSION = new Def("chain_explosion", Config.ENCH_CHAIN_EXPLOSION_MAX_LEVEL::get, 2, 6, 8, 6, true);
    public static final Def PREEMPTIVE_STRIKE = new Def("preemptive_strike", Config.ENCH_PREEMPTIVE_STRIKE_MAX_LEVEL::get, 2, 6, 8, 6, true);
    public static final Def COLLECTOR = new Def("collector", Config.ENCH_COLLECTOR_MAX_LEVEL::get, 1, 8, 8, 6, true);
    public static final Def EXPLOSION_EXPERT = new Def("explosion_expert", Config.ENCH_EXPLOSION_EXPERT_MAX_LEVEL::get, 1, 8, 8, 6, true);
    public static final Def LIFE_LEECH = new Def("life_leech", Config.ENCH_LIFE_LEECH_MAX_LEVEL::get, 1, 8, 8, 6, true);
    public static final Def SNIPER_ELITE = new Def("sniper_elite", Config.ENCH_SNIPER_ELITE_MAX_LEVEL::get, 1, 8, 8, 6, true);
    public static final Def PANDORA_PARADOX = new Def("pandora_paradox", Config.ENCH_PANDORA_PARADOX_MAX_LEVEL::get, 1, 8, 8, 6, true);
    public static final Def SMART_SCOPE = new Def("smart_scope", Config.ENCH_SMART_SCOPE_MAX_LEVEL::get, 1, 20, 10, 8, true);
    public static final Def DEEP_LEARNING = new Def("deep_learning", Config.ENCH_DEEP_LEARNING_MAX_LEVEL::get, 1, 5, 5, 4, true);
    public static final Def EQUALIZER = new Def("equalizer", Config.ENCH_EQUALIZER_MAX_LEVEL::get, 1, 12, 8, 6, true);
    public static final Def RANDOM = new Def("random", Config.ENCH_RANDOM_MAX_LEVEL::get, 1, 10, 8, 6, true);
    public static final Def DECAPITATION = new Def("decapitation", Config.ENCH_DECAPITATION_MAX_LEVEL::get, 1, 10, 8, 6, true);
    public static final Def CHARGE = new Def("charge", Config.ENCH_CHARGE_MAX_LEVEL::get, 1, 5, 5, 4, false);
    public static final Def ABYSS_GAZER = new Def("abyssgazer", Config.ENCH_ABYSSGAZER_MAX_LEVEL::get, 1, 10, 8, 8, false);
    public static final Def DOUBLE_SHOT = new Def("double_shot", Config.ENCH_DOUBLE_SHOT_MAX_LEVEL::get, 2, 5, 2, 4, false);
    public static final Def FOCUSED_AMMO = new Def("focused_ammo", Config.ENCH_FOCUSED_AMMO_MAX_LEVEL::get, 2, 5, 2, 4, false);
    public static final Def ARCANA_EDEN = new Def("arcana_eden", Config.ENCH_ARCANA_EDEN_MAX_LEVEL::get, 1, 6, 4, 6, false);
    public static final Def PATIENCE = new Def("patience", Config.ENCH_PATIENCE_MAX_LEVEL::get, 2, 5, 2, 4, false);

    private GunEnchantments() {
    }
}
