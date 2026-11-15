package com.ssscript.taczfixes.common.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 附魔注册键。最大等级/权重/铁砧费用/生效槽位等全部由
 * {@code data/taczfixes/enchantment/*.json} 定义, 这里不再重复配置。
 */
public final class GunEnchantments {
    public static final class Def {
        private final ResourceKey<Enchantment> key;

        private Def(String path) {
            this.key = ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.fromNamespaceAndPath("taczfixes", path));
        }

        public ResourceKey<Enchantment> get() {
            return key;
        }
    }

    public static final Def OVERLOAD = new Def("overload");
    public static final Def ANNIHILATION = new Def("annihilation");
    public static final Def STABILITY = new Def("stability");
    public static final Def ANTI_GRAVITY = new Def("anti_gravity");
    public static final Def ELECTROMAGNETIC_COIL = new Def("electromagnetic_coil");
    public static final Def STANDARD_AMMO = new Def("standard_ammo");
    public static final Def NEUROTOXIN = new Def("neurotoxin");
    public static final Def CHAIN_EXPLOSION = new Def("chain_explosion");
    public static final Def PREEMPTIVE_STRIKE = new Def("preemptive_strike");
    public static final Def COLLECTOR = new Def("collector");
    public static final Def EXPLOSION_EXPERT = new Def("explosion_expert");
    public static final Def LIFE_LEECH = new Def("life_leech");
    public static final Def SNIPER_ELITE = new Def("sniper_elite");
    public static final Def PANDORA_PARADOX = new Def("pandora_paradox");
    public static final Def SMART_SCOPE = new Def("smart_scope");
    public static final Def DEEP_LEARNING = new Def("deep_learning");
    public static final Def EQUALIZER = new Def("equalizer");
    public static final Def RANDOM = new Def("random");
    public static final Def DECAPITATION = new Def("decapitation");
    public static final Def CHARGE = new Def("charge");
    public static final Def ABYSS_GAZER = new Def("abyssgazer");
    public static final Def DOUBLE_SHOT = new Def("double_shot");
    public static final Def FOCUSED_AMMO = new Def("focused_ammo");
    public static final Def ARCANA_EDEN = new Def("arcana_eden");
    public static final Def PATIENCE = new Def("patience");

    private GunEnchantments() {
    }
}