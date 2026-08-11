package com.ssscript.taczfixes;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.DoubleValue LIMB_THRESHOLD_STANDING;
    public static final ForgeConfigSpec.DoubleValue LIMB_THRESHOLD_SNEAKING;
    public static final ForgeConfigSpec.DoubleValue LIMB_FACTOR_DEFAULT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BURST_BLOCK_ATTACHMENTS;
    public static final ForgeConfigSpec.BooleanValue LIVING_ENTITY_LIMB_ENABLED;
    public static final ForgeConfigSpec.DoubleValue LIVING_ENTITY_LIMB_THRESHOLD;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LIVING_ENTITY_LIMB_EXCLUDED;
    public static final ForgeConfigSpec.DoubleValue GUN_TYPE_PISTOL;
    public static final ForgeConfigSpec.DoubleValue GUN_TYPE_RIFLE;
    public static final ForgeConfigSpec.DoubleValue GUN_TYPE_SNIPER;
    public static final ForgeConfigSpec.DoubleValue GUN_TYPE_SHOTGUN;
    public static final ForgeConfigSpec.DoubleValue GUN_TYPE_SMG;
    public static final ForgeConfigSpec.DoubleValue GUN_TYPE_RPG;
    public static final ForgeConfigSpec.DoubleValue GUN_TYPE_MG;
    public static final ForgeConfigSpec.DoubleValue GUN_TYPE_OTHER;
    public static final ForgeConfigSpec.BooleanValue DISABLE_ARCANA_MAGNIFICATION_FOR_SIGHT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HIDE_PARTICLES_IN_ARCANA_THERMAL;
    public static final ForgeConfigSpec.BooleanValue PARCOOL_SLIDE_AS_MOVE_INACCURACY;
    public static final ForgeConfigSpec.BooleanValue DISABLE_TRACKING_AFTER_PENETRATION;
    public static final ForgeConfigSpec.DoubleValue PEEK_HEADSHOT_HEIGHT;
    public static final ForgeConfigSpec.BooleanValue AUTO_AIM_WHEN_PEEKING;
    public static final ForgeConfigSpec.BooleanValue ADS_INTERRUPT_SPRINT;
    public static final ForgeConfigSpec.DoubleValue SPREAD_RAMP_INCREMENT;
    public static final ForgeConfigSpec.DoubleValue SPREAD_RAMP_FLAT_INCREMENT;
    public static final ForgeConfigSpec.IntValue SPREAD_RAMP_MAX_STACKS;
    public static final ForgeConfigSpec.IntValue SPREAD_RAMP_DECAY_DELAY_MS;
    public static final ForgeConfigSpec.DoubleValue SPREAD_RAMP_DECAY;
    public static final ForgeConfigSpec.BooleanValue EXPLOSION_BULLET_ONLY;
    public static final ForgeConfigSpec.BooleanValue RECOIL_FIRE_RATE_REDUCTION_ENABLED;
    public static final ForgeConfigSpec.IntValue RECOIL_FIRE_RATE_WINDOW;
    public static final ForgeConfigSpec.DoubleValue RECOIL_FIRE_RATE_FACTOR;
    public static final ForgeConfigSpec.DoubleValue RECOIL_FIRE_RATE_PAUSE_FACTOR_PITCH;
    public static final ForgeConfigSpec.DoubleValue RECOIL_FIRE_RATE_PAUSE_FACTOR_YAW;
    public static final ForgeConfigSpec.IntValue RECOIL_FIRE_RATE_MIN_RPM;
    public static final ForgeConfigSpec.IntValue GUN_LEVEL_MAX_LEVEL;
    public static final ForgeConfigSpec.IntValue GUN_LEVEL_BASE_KILLS;
    public static final ForgeConfigSpec.IntValue GUN_LEVEL_INCREMENT;
    public static final ForgeConfigSpec.BooleanValue BULLET_RICOCHET_ENABLE;
    public static final ForgeConfigSpec.DoubleValue BULLET_RICOCHET_MIN_ANGLE;
    public static final ForgeConfigSpec.DoubleValue BULLET_RICOCHET_MAX_ANGLE;
    public static final ForgeConfigSpec.DoubleValue BULLET_RICOCHET_CHANCE_MIN;
    public static final ForgeConfigSpec.DoubleValue BULLET_RICOCHET_CHANCE_MAX;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BULLET_RICOCHET_BLOCK_TAGS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BULLET_RICOCHET_DISABLED_GUNS;
    public static final ForgeConfigSpec.DoubleValue BULLET_RICOCHET_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue BULLET_RICOCHET_REFLECT_ANGLE_RATIO_MIN;
    public static final ForgeConfigSpec.DoubleValue BULLET_RICOCHET_REFLECT_ANGLE_RATIO_MAX;
    public static final ForgeConfigSpec.BooleanValue BULLET_RICOCHET_TOP_BOTTOM_ENABLE;
    public static final ForgeConfigSpec.BooleanValue RECOIL_KNOCKBACK_ENABLED;
    public static final ForgeConfigSpec.DoubleValue RECOIL_KNOCKBACK_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue RECOIL_KNOCKBACK_SNEAK_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue RECOIL_KNOCKBACK_SEMI_FACTOR;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RECOIL_FIRE_RATE_DISABLED_GUNS;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PENETRATION_BLOCKED_ENTITIES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DAMAGE_REDUCTION_ENTITIES;
    public static final ForgeConfigSpec.BooleanValue BULLET_IGNORE_ENTITY_ENABLE;
    public static final ForgeConfigSpec.IntValue BULLET_IGNORE_ENTITY_COOLDOWN_MS;
    public static final ForgeConfigSpec.BooleanValue DISABLE_HITBOXES;
    public static final ForgeConfigSpec.BooleanValue DISABLE_THIRD_PERSON;
    public static final ForgeConfigSpec.BooleanValue WALL_HUG_ENTITY_HIT_ENABLE;

    public static final ForgeConfigSpec.BooleanValue GUN_ENCHANTMENT_ENABLED;
    public static final ForgeConfigSpec.IntValue GUN_ENCHANTMENT_VALUE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUN_ENCHANT_WHITELIST;
    public static final ForgeConfigSpec.BooleanValue GUN_ENCHANT_IGNORE_CONFLICT;
    public static final ForgeConfigSpec.DoubleValue ENCH_POWER_BULLET_MULT;
    public static final ForgeConfigSpec.DoubleValue ENCH_SMITE_BULLET_MULT;
    public static final ForgeConfigSpec.DoubleValue ENCH_BANE_BULLET_MULT;
    public static final ForgeConfigSpec.DoubleValue ENCH_IMPALING_BULLET_MULT;
    public static final ForgeConfigSpec.DoubleValue ENCH_PUNCH_BULLET_KNOCKBACK_MULT;
    public static final ForgeConfigSpec.DoubleValue ENCH_PUNCH_BULLET_KNOCKBACK_FLAT;
    public static final ForgeConfigSpec.IntValue ENCH_FLAME_IGNITE_TICKS;
    public static final ForgeConfigSpec.IntValue ENCH_PIERCE_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_UNBREAKING_NO_CONSUME_CHANCE;
    public static final ForgeConfigSpec.IntValue ENCH_MENDING_AMMO_PER_KILL;
    public static final ForgeConfigSpec.DoubleValue ENCH_SHARPNESS_MELEE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue ENCH_SWEEPING_DISTANCE_MULT;
    public static final ForgeConfigSpec.DoubleValue ENCH_KNOCKBACK_MELEE_MULT;
    public static final ForgeConfigSpec.DoubleValue ENCH_KNOCKBACK_MELEE_FLAT;
    public static final ForgeConfigSpec.IntValue ENCH_FIRE_ASPECT_MELEE_TICKS;
    public static final ForgeConfigSpec.IntValue ENCH_STEAL_LIGHTNING_COOLDOWN_MS;
    public static final ForgeConfigSpec.DoubleValue ENCH_CHANNELING_TRIGGER_CHANCE;
    public static final ForgeConfigSpec.BooleanValue ENCH_CHANNELING_ONLY_THUNDER;
    public static final ForgeConfigSpec.IntValue ENCH_LOYALTY_RANGE_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_LOYALTY_ANGLE_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_MULTISHOT_EXTRA_COUNT;
    public static final ForgeConfigSpec.DoubleValue ENCH_MULTISHOT_TRIGGER_CHANCE;
    public static final ForgeConfigSpec.DoubleValue ENCH_MULTISHOT_SPREAD_ANGLE;
    public static final ForgeConfigSpec.IntValue ENCH_MULTISHOT_COOLDOWN_MS;
    public static final ForgeConfigSpec.DoubleValue ENCH_RIPTIDE_SPEED_MULT;
    public static final ForgeConfigSpec.DoubleValue ENCH_RIPTIDE_DAMAGE_MULT;
    public static final ForgeConfigSpec.DoubleValue ENCH_QUICK_CHARGE_TIME_REDUCTION;
    public static final ForgeConfigSpec.DoubleValue ENCH_EFFICIENCY_FIRE_RATE_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_EFFICIENCY_BOLT_TIME_REDUCTION_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_OVERLOAD_DAMAGE_PERCENT;
    public static final ForgeConfigSpec.IntValue ENCH_OVERLOAD_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_ANNIHILATION_DAMAGE_PERCENT;
    public static final ForgeConfigSpec.IntValue ENCH_ANNIHILATION_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_STABILITY_RECOIL_REDUCTION_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_STABILITY_MAX_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_ANTIGRAVITY_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_COIL_SPEED_PERCENT;
    public static final ForgeConfigSpec.DoubleValue ENCH_COIL_SPREAD_REDUCTION_PERCENT;
    public static final ForgeConfigSpec.DoubleValue ENCH_COIL_LIGHTNING_CHANCE_PERCENT;
    public static final ForgeConfigSpec.DoubleValue ENCH_COIL_LIGHTNING_DAMAGE_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_COIL_MAX_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_STANDARD_AMMO_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_FORTUNE_HEADSHOT_CHANCE_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_NEUROTOXIN_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_NEUROTOXIN_TRIGGER_CHANCE_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_NEUROTOXIN_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue ENCH_CHAIN_EXPLOSION_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_CHAIN_EXPLOSION_BASE_CHANCE_PERCENT;
    public static final ForgeConfigSpec.DoubleValue ENCH_CHAIN_EXPLOSION_CHANCE_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_CHAIN_EXPLOSION_RADIUS_MIN;
    public static final ForgeConfigSpec.DoubleValue ENCH_CHAIN_EXPLOSION_RADIUS_SCALE_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_CHAIN_EXPLOSION_DAMAGE_BASE;
    public static final ForgeConfigSpec.DoubleValue ENCH_CHAIN_EXPLOSION_DAMAGE_SCALE_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_PREEMPTIVE_STRIKE_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_PREEMPTIVE_STRIKE_DAMAGE_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_COLLECTOR_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_COLLECTOR_DAMAGE_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_STANDARD_AMMO_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_EXPLOSION_EXPERT_MAX_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_LIFE_LEECH_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_LIFE_LEECH_HEAL_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_SNIPER_ELITE_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_SNIPER_ELITE_HEADSHOT_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_PANDORA_PARADOX_MAX_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_NEUROTOXIN_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_CHAIN_EXPLOSION_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_PREEMPTIVE_STRIKE_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_ANNIHILATION_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_COIL_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_ANTIGRAVITY_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_STABILITY_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_OVERLOAD_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_COLLECTOR_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_EXPLOSION_EXPERT_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_LIFE_LEECH_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_SNIPER_ELITE_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_PANDORA_PARADOX_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_SMART_SCOPE_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_SMART_SCOPE_MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue ENCH_DEEP_LEARNING_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_DEEP_LEARNING_DAMAGE_PERCENT_PER_GUN_LEVEL_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_EQUALIZER_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_EQUALIZER_TRIGGER_CHANCE_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_RANDOM_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_RANDOM_EFFECT_CHANCE_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_RANDOM_EFFECT_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue ENCH_DECAPITATION_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_DECAPITATION_HEADSHOT_BONUS_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_SMART_SCOPE_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_DEEP_LEARNING_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_EQUALIZER_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_RANDOM_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_DECAPITATION_ANVIL_MULT;
    public static final ForgeConfigSpec.IntValue ENCH_CHARGE_MAX_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENCH_CHARGE_DAMAGE_PERCENT_PER_SPEED_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue ENCH_CHARGE_ANVIL_MULT;

    public static final ForgeConfigSpec.BooleanValue GUN_BOTTLE_ENABLED;
    public static final ForgeConfigSpec.IntValue GUN_BOTTLE_EXP_PER_BOTTLE;
    public static final ForgeConfigSpec.IntValue GUN_BOTTLE_COST;

    public static final ForgeConfigSpec.BooleanValue STEPLESS_ZOOM_ENABLED;
    public static final ForgeConfigSpec.DoubleValue STEPLESS_ZOOM_CTRL_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue STEPLESS_ZOOM_ALT_MULTIPLIER;

    static {
        BUILDER.push("gun_level");
        GUN_LEVEL_MAX_LEVEL = BUILDER
                .comment("枪械的等级上限。默认值：500")
                .defineInRange("max_level", 500, 1, 10000);
        GUN_LEVEL_BASE_KILLS = BUILDER
                .comment("从0级到1级所需的击杀数。默认值：1")
                .defineInRange("base_kills", 1, 1, 100000);
        GUN_LEVEL_INCREMENT = BUILDER
                .comment("每升1级，升级额外所需的击杀数。默认值：5")
                .defineInRange("level_increment", 5, 0, 100000);
        GUN_BOTTLE_ENABLED = BUILDER
                .comment("是否启用铁砧使用附魔之瓶为枪械增加经验。默认值：true")
                .define("bottle_enabled", true);
        GUN_BOTTLE_EXP_PER_BOTTLE = BUILDER
                .comment("每瓶附魔之瓶增加的枪械经验值。默认值：5")
                .defineInRange("bottle_exp_per_bottle", 5, 1, 10000);
        GUN_BOTTLE_COST = BUILDER
                .comment("铁砧使用附魔之瓶消耗的玩家经验等级。默认值：1")
                .defineInRange("bottle_cost", 1, 0, 100);
        BUILDER.pop();

        BUILDER.push("stepless_zoom");
        STEPLESS_ZOOM_ENABLED = BUILDER
                .comment("是否启用瞄具无极变倍功能。默认值：true")
                .define("enabled", true);
        STEPLESS_ZOOM_CTRL_MULTIPLIER = BUILDER
                .comment("按住 Ctrl 滚动滚轮时，倍率调整速度的倍率。默认值：4")
                .defineInRange("zoom_ctrl_multiplier", 4.0, 1.0, 10.0);
        STEPLESS_ZOOM_ALT_MULTIPLIER = BUILDER
                .comment("按住 Alt 滚动滚轮时，倍率调整速度的倍率。默认值：0.25")
                .defineInRange("zoom_alt_multiplier", 0.25, 0.1, 1.0);
        BUILDER.pop();

        BUILDER.push("limb_damage_multiplier");

        BUILDER.push("player");
        LIMB_THRESHOLD_STANDING = BUILDER
                .comment("玩家在站立时，碰撞箱低于此高度的部分将视为四肢。默认值：0.8")
                .defineInRange("limb_threshold_standing", 0.8, 0.0, 1.0);
        LIMB_THRESHOLD_SNEAKING = BUILDER
                .comment("玩家在潜行时，碰撞箱低于此高度的部分将视为四肢。默认值：0.6")
                .defineInRange("limb_threshold_sneaking", 0.6, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("gun_types");
        LIMB_FACTOR_DEFAULT = BUILDER
                .comment("默认的四肢伤害倍率。默认值：0.6")
                .defineInRange("default", 0.6, 0.0, 1.0);
        GUN_TYPE_PISTOL = BUILDER
                .comment("手枪的默认四肢伤害倍率。默认值：0.6")
                .defineInRange("pistol", 0.6, 0.0, 1.0);
        GUN_TYPE_RIFLE = BUILDER
                .comment("步枪的默认四肢伤害倍率。默认值：0.6")
                .defineInRange("rifle", 0.6, 0.0, 1.0);
        GUN_TYPE_SNIPER = BUILDER
                .comment("狙击枪的默认四肢伤害倍率。默认值：0.7")
                .defineInRange("sniper", 0.7, 0.0, 1.0);
        GUN_TYPE_SHOTGUN = BUILDER
                .comment("霰弹枪的默认四肢伤害倍率。默认值：0.8")
                .defineInRange("shotgun", 0.8, 0.0, 1.0);
        GUN_TYPE_SMG = BUILDER
                .comment("冲锋枪的默认四肢伤害倍率。默认值：0.5")
                .defineInRange("smg", 0.5, 0.0, 1.0);
        GUN_TYPE_RPG = BUILDER
                .comment("重型武器的默认四肢伤害倍率。默认值：0.8")
                .defineInRange("rpg", 0.8, 0.0, 1.0);
        GUN_TYPE_MG = BUILDER
                .comment("机枪的默认四肢伤害倍率。默认值：0.7")
                .defineInRange("mg", 0.7, 0.0, 1.0);
        GUN_TYPE_OTHER = BUILDER
                .comment("其他类型枪械的默认四肢伤害倍率。默认值：0.6")
                .defineInRange("other", 0.6, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("mobs");
        LIVING_ENTITY_LIMB_ENABLED = BUILDER
                .comment("是否对非玩家实体启用四肢伤害倍率。默认值：true")
                .define("enabled", true);
        LIVING_ENTITY_LIMB_THRESHOLD = BUILDER
                .comment("生物的碰撞箱的下半部分按此比例视为四肢。默认值：0.4")
                .defineInRange("threshold", 0.4, 0.0, 1.0);
        LIVING_ENTITY_LIMB_EXCLUDED = BUILDER
                .comment("禁用四肢伤害倍率的实体列表。",
                        "Example: [\"minecraft:ender_dragon\"]")
                .defineList("excluded", List.of("minecraft:ender_dragon"), it -> it instanceof String);
        BUILDER.pop();

        BUILDER.pop();

        BUILDER.push("recoil_modifier");
        RECOIL_FIRE_RATE_REDUCTION_ENABLED = BUILDER
                .comment("是否启用首发后坐力倍率。默认值：true")
                .define("enabled", true);
        RECOIL_FIRE_RATE_WINDOW = BUILDER
                .comment("距离上次开火的时间超出此数值后开火，将视为首次开火。默认值：200")
                .defineInRange("window_ms", 200, 0, 10000);
        RECOIL_FIRE_RATE_FACTOR = BUILDER
                .comment("非首次开火的后坐力倍率。默认值：1.0")
                .defineInRange("factor", 1.0, 0.0, 1.0);
        RECOIL_FIRE_RATE_PAUSE_FACTOR_PITCH = BUILDER
                .comment("首次开火的竖直后坐力倍率。默认值：3.0")
                .defineInRange("pause_factor_pitch", 3.0, 1.0, 10.0);
        RECOIL_FIRE_RATE_PAUSE_FACTOR_YAW = BUILDER
                .comment("首次开火的水平后坐力倍率。默认值：1.5")
                .defineInRange("pause_factor_yaw", 1.5, 1.0, 10.0);
        RECOIL_FIRE_RATE_MIN_RPM = BUILDER
                .comment("触发首发后坐力倍率所需的最小射速。默认值：200")
                .defineInRange("min_rpm", 300, 0, 1200);
        RECOIL_FIRE_RATE_DISABLED_GUNS = BUILDER
                .comment("禁用首发后坐力倍率的枪械列表。",
                        "Example: [\"rfp:m2hb\", \"rfp:dshkm\"]")
                .defineList("disabled_guns", List.of("rfp:m2hb", "rfp:dshkm"), it -> it instanceof String);
        BUILDER.pop();

        BUILDER.push("inaccuracy_modifier");
        SPREAD_RAMP_INCREMENT = BUILDER
                .comment("每次开火增加的散布比例。默认值：0.03")
                .defineInRange("increment", 0.03, 0.0, 1.0);
        SPREAD_RAMP_FLAT_INCREMENT = BUILDER
                .comment("每次开火增加的散布。默认值：0.02")
                .defineInRange("flat_increment", 0.02, 0.0, 10.0);
        SPREAD_RAMP_MAX_STACKS = BUILDER
                .comment("连射惩罚的最大应用次数。默认值：30")
                .defineInRange("max_stacks", 30, 0, 1000);
        SPREAD_RAMP_DECAY_DELAY_MS = BUILDER
                .comment("距离上次开火时间超出此数值后将开始回正散布。默认值：200")
                .defineInRange("decay_delay_ms", 200, 0, 10000);
        SPREAD_RAMP_DECAY = BUILDER
                .comment("回正散布的速度。默认值：0.1")
                .defineInRange("decay", 0.10, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("bullet_ricochet");
        BULLET_RICOCHET_ENABLE = BUILDER
                .comment("是否启用跳弹。默认值：true")
                .define("enable", true);
        BULLET_RICOCHET_MIN_ANGLE = BUILDER
                .comment("跳弹所需的最小入射角。默认值：60")
                .defineInRange("min_angle", 60.0, 0.0, 90.0);
        BULLET_RICOCHET_MAX_ANGLE = BUILDER
                .comment("超出此入射角后跳弹概率等同于最大跳弹概率。默认值：90")
                .defineInRange("max_angle", 90.0, 0.0, 90.0);
        BULLET_RICOCHET_CHANCE_MIN = BUILDER
                .comment("以跳弹所需的最小入射角撞击方块时发生跳弹的概率。默认值：0")
                .defineInRange("chance_min", 0.0, 0.0, 1.0);
        BULLET_RICOCHET_CHANCE_MAX = BUILDER
                .comment("最大跳弹概率。默认值：1")
                .defineInRange("chance_max", 1.0, 0.0, 1.0);
        BULLET_RICOCHET_BLOCK_TAGS = BUILDER
                .comment("子弹碰撞方块时，会发生跳弹的方块列表。",
                        "Example: [\"minecraft:mineable/pickaxe\"]")
                .defineList("block_tags", List.of("minecraft:mineable/pickaxe"), it -> it instanceof String);
        BULLET_RICOCHET_DISABLED_GUNS = BUILDER
                .comment("禁用跳弹的枪械列表。",
                        "Example: [\"ts:c4\"]")
                .defineList("disabled_guns", List.of("ts:c4"), it -> it instanceof String);
        BULLET_RICOCHET_DAMAGE_MULTIPLIER = BUILDER
                .comment("子弹发生跳弹后造成伤害的倍率。默认值：0.2")
                .defineInRange("damage_multiplier", 0.2, 0.0, 1.0);
        BULLET_RICOCHET_REFLECT_ANGLE_RATIO_MIN = BUILDER
                .comment("每次跳弹时，反射角的余角与入射角的余角之比的最小值。默认值：0.2")
                .defineInRange("reflect_angle_ratio_min", 0.2, 0.0, 1.0);
        BULLET_RICOCHET_REFLECT_ANGLE_RATIO_MAX = BUILDER
                .comment("每次跳弹时，反射角的余角与入射角的余角之比的最大值。默认值：0.8")
                .defineInRange("reflect_angle_ratio_max", 0.8, 0.0, 1.0);
        BULLET_RICOCHET_TOP_BOTTOM_ENABLE = BUILDER
                .comment("是否允许子弹在命中方块顶面或底面时发生跳弹。默认值：false")
                .define("top_bottom_enable", false);
        BUILDER.pop();

        BUILDER.push("recoil_knockback");
        RECOIL_KNOCKBACK_ENABLED = BUILDER
                .comment("是否启用开火击退。默认值：true")
                .define("enable", true);
        RECOIL_KNOCKBACK_MULTIPLIER = BUILDER
                .comment("开火击退的力度大小。默认值：0.015")
                .defineInRange("multiplier", 0.015, 0.0, 1.0);
        RECOIL_KNOCKBACK_SNEAK_MULTIPLIER = BUILDER
                .comment("潜行时开火击退的力度大小。默认值：0.005")
                .defineInRange("sneak_multiplier", 0.005, 0.0, 1.0);
        RECOIL_KNOCKBACK_SEMI_FACTOR = BUILDER
                .comment("半自动模式的开火击退力度倍率。默认值：4")
                .defineInRange("semi_factor", 4.0, 1.0, 10.0);
        BUILDER.pop();

        BUILDER.push("misc");

        BUILDER.push("burst_fire");
        BURST_BLOCK_ATTACHMENTS = BUILDER
                .comment("禁用连发模式的配件列表。",
                        "Example: [\"ccrp:ammo_mod_hap\"]")
                .defineList("burst_block_attachments", List.of("ccrp:ammo_mod_hap"), it -> it instanceof String);
        BUILDER.pop();

        BUILDER.push("explosion");
        EXPLOSION_BULLET_ONLY = BUILDER
                .comment("爆炸击退是否仅对子弹生效。默认值：true")
                .define("bullet_only", false);
        BUILDER.pop();

        BUILDER.push("peek_aim");
        AUTO_AIM_WHEN_PEEKING = BUILDER
                .comment("是否启用探头自动开镜。默认值：true",
                        "需要GD656Peek模组。")
                .define("auto_aim_when_peeking", false);
        BUILDER.pop();

        BUILDER.push("unpierceable");
        PENETRATION_BLOCKED_ENTITIES = BUILDER
                .comment("子弹无法穿透的实体列表。",
                        "Example: [\"irons_spellbooks:shield\"]")
                .defineList("entities", List.of("irons_spellbooks:shield"), it -> it instanceof String);
        BUILDER.pop();

        BUILDER.push("damage");
        DAMAGE_REDUCTION_ENTITIES = BUILDER
                .comment("降低指定实体所受子弹伤害的列表。格式: \"entity_id,reduction\"",
                        "\"entity_id\"为其命名空间id，\"reduction\"为其降低伤害的比例。")
                .defineList("entities", List.of("irons_spellbooks:shield,0.95"), it -> it instanceof String);
        BULLET_IGNORE_ENTITY_ENABLE = BUILDER
                .comment("子弹命中实体后，此子弹是否在一定时间内忽略此实体。默认值：true")
                .define("ignore_damaged_entity_enable", true);
        BULLET_IGNORE_ENTITY_COOLDOWN_MS = BUILDER
                .comment("子弹命中实体后，此子弹忽略此实体的时间。默认值：5000")
                .defineInRange("ignore_damaged_entity_cooldown_ms", 5000, 0, 600000);
        BUILDER.pop();

        BUILDER.push("debug");
        DISABLE_HITBOXES = BUILDER
                .comment("禁用实体碰撞箱显示。默认值：false")
                .define("disable_hitboxes", false);
        DISABLE_THIRD_PERSON = BUILDER
                .comment("禁用切换第三人称视角。默认值：false")
                .define("disable_third_person", false);
        WALL_HUG_ENTITY_HIT_ENABLE = BUILDER
                .comment("是否修复子弹无法命中紧贴方块的薄实体（画/物品展示框等）。默认值：true")
                .define("wall_hug_entity_hit_enable", true);
        BUILDER.pop();

        BUILDER.pop();

        BUILDER.push("compat");
        DISABLE_ARCANA_MAGNIFICATION_FOR_SIGHT = BUILDER
                .comment("是否在使用非筒状瞄具时禁用镜内放大。默认值：true",
                        "需要TaCZ: Arcana模组。")
                .define("disable_arcana_magnification_for_sight", true);
HIDE_PARTICLES_IN_ARCANA_THERMAL = BUILDER
                .comment("在热成像瞄具视野内隐藏的粒子列表。",
                        "需要TaCZ: Arcana模组。")
                .defineList("hide_particles_in_arcana_thermal",
                        List.of("me.xjqsh.lrtactical.client.particle.SmokeCloudParticle"),
                        it -> it instanceof String);
        PARCOOL_SLIDE_AS_MOVE_INACCURACY = BUILDER
                .comment("滑铲时，应用移动时而非爬行时的腰射散布。默认值：true",
                        "需要ParCool模组。")
                .define("parcool_slide_as_move_inaccuracy", true);
        DISABLE_TRACKING_AFTER_PENETRATION = BUILDER
                .comment("子弹穿透实体后是否失去追踪。默认值：true",
                        "需要GunsmithLib模组的追踪功能。")
                .define("disable_tracking_after_penetration", true);
        PEEK_HEADSHOT_HEIGHT = BUILDER
                .comment("玩家探头时，从碰撞箱顶部起视为头部的格数。默认值：0.6",
                        "需要GD656Peek模组。")
                .defineInRange("peek_headshot_height", 0.6, 0.0, 1.0);
        ADS_INTERRUPT_SPRINT = BUILDER
                .comment("开镜是否强制打断疾跑。默认值：true")
                .define("ads_interrupt_sprint", true);
        BUILDER.pop();

        BUILDER.push("gun_enchantment");
        GUN_ENCHANTMENT_ENABLED = BUILDER
                .comment("是否启用枪械附魔。默认值：true")
                .define("enabled", true);
        GUN_ENCHANTMENT_VALUE = BUILDER
                .comment("枪械的附魔能力值。默认值：25")
                .defineInRange("enchantment_value", 25, 1, 100);
        GUN_ENCHANT_WHITELIST = BUILDER
                .comment("允许的附魔列表。")
                .defineList("whitelist", List.of(
                        "minecraft:sharpness",
                        "minecraft:smite",
                        "minecraft:bane_of_arthropods",
                        "minecraft:knockback",
                        "minecraft:fire_aspect",
                        "minecraft:looting",
                        "minecraft:fortune",
                        "minecraft:efficiency",
                        "minecraft:silk_touch",
                        "minecraft:sweeping_edge",
                        "minecraft:power",
                        "minecraft:punch",
                        "minecraft:flame",
                        "minecraft:infinity",
                        "minecraft:loyalty",
                        "minecraft:impaling",
                        "minecraft:channeling",
                        "minecraft:piercing",
                        "minecraft:multishot",
                        "minecraft:riptide",
                        "minecraft:quick_charge",
                        "minecraft:unbreaking",
                        "minecraft:mending",
                        "minecraft:vanishing_curse",
                        "taczfixes:overload",
                        "taczfixes:annihilation",
                        "taczfixes:stability",
                        "taczfixes:anti_gravity",
                        "taczfixes:electromagnetic_coil",
                        "taczfixes:standard_ammo",
                        "taczfixes:neurotoxin",
                        "taczfixes:chain_explosion",
                        "taczfixes:preemptive_strike",
                        "taczfixes:collector",
                        "taczfixes:explosion_expert",
                        "taczfixes:life_leech",
                        "taczfixes:sniper_elite",
                        "taczfixes:pandora_paradox",
                        "taczfixes:smart_scope",
                        "taczfixes:deep_learning",
                        "taczfixes:equalizer",
                        "taczfixes:random",
                        "taczfixes:decapitation",
                        "taczfixes:charge"), it -> it instanceof String);
        GUN_ENCHANT_IGNORE_CONFLICT = BUILDER
                .comment("是否允许冲突附魔同时存在。",
                        "默认值：true")
                .define("ignore_conflicts", true);

        BUILDER.push("common");
        ENCH_POWER_BULLET_MULT = BUILDER
                .comment("力量：每级提升的子弹伤害倍率。默认值：0.1")
                .defineInRange("power_damage_multiplier_per_level", 0.1, 0.0, 10.0);
        ENCH_SMITE_BULLET_MULT = BUILDER
                .comment("亡灵杀手：每级提升的对亡灵生物的子弹伤害倍率。默认值：0.25")
                .defineInRange("smite_damage_multiplier_per_level", 0.25, 0.0, 10.0);
        ENCH_BANE_BULLET_MULT = BUILDER
                .comment("节肢杀手：每级提升的对节肢生物的子弹伤害倍率。默认值：0.25")
                .defineInRange("bane_of_arthropods_damage_multiplier_per_level", 0.25, 0.0, 10.0);
        ENCH_IMPALING_BULLET_MULT = BUILDER
                .comment("穿刺：每级提升的对水生生物的子弹伤害倍率。默认值：0.25")
                .defineInRange("impaling_damage_multiplier_per_level", 0.25, 0.0, 10.0);
        ENCH_FLAME_IGNITE_TICKS = BUILDER
                .comment("火矢：每级增加的燃烧时间。默认值：20")
                .defineInRange("flame_ignite_ticks_per_level", 20, 0, 1200);
        ENCH_PIERCE_PER_LEVEL = BUILDER
                .comment("穿透：每级增加的穿透实体数量。默认值：1")
                .defineInRange("pierce_per_level", 1, 0, 50);
        ENCH_UNBREAKING_NO_CONSUME_CHANCE = BUILDER
                .comment("耐久：每次开火时，每级不消耗子弹的概率。默认值：0.15")
                .defineInRange("unbreaking_no_consume_chance_per_level", 0.15, 0.0, 1.0);
        ENCH_MENDING_AMMO_PER_KILL = BUILDER
                .comment("经验修补：每次击杀后补充到弹匣的子弹数量。默认值：1")
                .defineInRange("mending_ammo_per_kill", 1, 0, 64);
        ENCH_SHARPNESS_MELEE_DAMAGE = BUILDER
                .comment("锋利：每级提升的近战伤害。默认值：2")
                .defineInRange("sharpness_melee_damage_per_level", 2.0, 0.0, 100.0);
        ENCH_SWEEPING_DISTANCE_MULT = BUILDER
                .comment("横扫之刃：每级提升的近战距离倍率。默认值：0.25")
                .defineInRange("sweeping_distance_multiplier_per_level", 0.25, 0.0, 10.0);
        ENCH_FIRE_ASPECT_MELEE_TICKS = BUILDER
                .comment("火焰附加：每级增加的近战攻击点燃实体时间。默认值：80")
                .defineInRange("fire_aspect_melee_ticks_per_level", 80, 0, 1200);
        ENCH_QUICK_CHARGE_TIME_REDUCTION = BUILDER
                .comment("快速装填：每级减少的换弹时间。默认值：0.1")
                .defineInRange("quick_charge_reload_time_reduction_per_level", 0.1, 0.0, 0.9);
        ENCH_FORTUNE_HEADSHOT_CHANCE_PERCENT_PER_LEVEL = BUILDER
                .comment("时运：每级必定爆头的概率。默认值：25")
                .defineInRange("fortune_headshot_chance_percent_per_level", 25.0, 0.0, 100.0);
        BUILDER.pop();

        BUILDER.push("punch");
        ENCH_PUNCH_BULLET_KNOCKBACK_MULT = BUILDER
                .comment("冲击：每级提升的子弹击退倍率。默认值：0.5")
                .defineInRange("punch_knockback_multiplier_per_level", 0.5, 0.0, 10.0);
        ENCH_PUNCH_BULLET_KNOCKBACK_FLAT = BUILDER
                .comment("冲击：每级增加的子弹击退力度。默认值：0.5")
                .defineInRange("punch_knockback_flat_per_level", 0.5, 0.0, 100.0);
        BUILDER.pop();

        BUILDER.push("knockback");
        ENCH_KNOCKBACK_MELEE_MULT = BUILDER
                .comment("击退：每级提升的近战击退倍率。默认值：0.5")
                .defineInRange("knockback_melee_multiplier_per_level", 0.5, 0.0, 10.0);
        ENCH_KNOCKBACK_MELEE_FLAT = BUILDER
                .comment("击退：每级增加的近战击退力度。默认值：2")
                .defineInRange("knockback_melee_flat_per_level", 2.0, 0.0, 100.0);
        BUILDER.pop();

        BUILDER.push("channeling");
        ENCH_STEAL_LIGHTNING_COOLDOWN_MS = BUILDER
                .comment("引雷：冷却时间。默认值：5000")
                .defineInRange("channeling_cooldown_ms", 5000, 0, 600000);
        ENCH_CHANNELING_TRIGGER_CHANCE = BUILDER
                .comment("引雷：触发概率。默认值：0.3")
                .defineInRange("channeling_trigger_chance", 0.3, 0.0, 1.0);
        ENCH_CHANNELING_ONLY_THUNDER = BUILDER
                .comment("引雷：是否仅在雷暴天气触发。默认值：false")
                .define("channeling_only_in_thunder", false);
        BUILDER.pop();

        BUILDER.push("loyalty");
        ENCH_LOYALTY_RANGE_PER_LEVEL = BUILDER
                .comment("忠诚：每级增加的自动锁定距离，需要GunsmithLib。默认值：80")
                .defineInRange("loyalty_aim_lock_range_per_level", 80, 0, 10000);
        ENCH_LOYALTY_ANGLE_PER_LEVEL = BUILDER
                .comment("忠诚：每级增加的自动锁定角度，需要GunsmithLib。默认值：5")
                .defineInRange("loyalty_aim_lock_angle_per_level", 5, 0, 1000);
        BUILDER.pop();

        BUILDER.push("multishot");
        ENCH_MULTISHOT_EXTRA_COUNT = BUILDER
                .comment("多重射击：每次触发额外发射的弹丸数量。默认值：2")
                .defineInRange("multishot_extra_projectiles", 2, 1, 10);
        ENCH_MULTISHOT_TRIGGER_CHANCE = BUILDER
                .comment("多重射击：每级触发概率。默认值：0.3")
                .defineInRange("multishot_trigger_chance_per_level", 0.3, 0.0, 1.0);
        ENCH_MULTISHOT_SPREAD_ANGLE = BUILDER
                .comment("多重射击：额外弹丸的偏转角度。默认值：10")
                .defineInRange("multishot_spread_angle", 10.0, 0.0, 45.0);
        ENCH_MULTISHOT_COOLDOWN_MS = BUILDER
                .comment("多重射击：冷却时间。默认值：200")
                .defineInRange("multishot_cooldown_ms", 200, 0, 600000);
        BUILDER.pop();

        BUILDER.push("riptide");
        ENCH_RIPTIDE_SPEED_MULT = BUILDER
                .comment("激流：每级提升的子弹飞行速度倍率。默认值：0.5")
                .defineInRange("riptide_speed_multiplier_per_level", 0.5, 0.0, 10.0);
        ENCH_RIPTIDE_DAMAGE_MULT = BUILDER
                .comment("激流：每级提升的子弹伤害倍率。默认值：0.25")
                .defineInRange("riptide_damage_multiplier_per_level", 0.25, 0.0, 10.0);
        BUILDER.pop();

        BUILDER.push("efficiency");
        ENCH_EFFICIENCY_FIRE_RATE_PERCENT_PER_LEVEL = BUILDER
                .comment("效率：每级提升的射速。默认值：3%")
                .defineInRange("efficiency_fire_rate_percent_per_level", 3.0, 0.0, 100.0);
        ENCH_EFFICIENCY_BOLT_TIME_REDUCTION_PERCENT_PER_LEVEL = BUILDER
                .comment("效率：每级缩减的栓动拉栓时长。默认值：3%")
                .defineInRange("efficiency_bolt_time_reduction_percent_per_level", 3.0, 0.0, 90.0);
        BUILDER.pop();

        BUILDER.push("overload");
        ENCH_OVERLOAD_DAMAGE_PERCENT = BUILDER
                .comment("过载：每级增加的伤害。默认值：50%")
                .defineInRange("overload_damage_percent", 50.0, 0.0, 1000.0);
        ENCH_OVERLOAD_MAX_LEVEL = BUILDER
                .comment("过载：附魔的最大等级。默认值：4")
                .defineInRange("overload_max_level", 4, 1, 10);
        ENCH_OVERLOAD_ANVIL_MULT = BUILDER
                .comment("过载：经验等级乘数。",
                        "默认值：4")
                .defineInRange("overload_cost_multiplier", 4, 1, 100);
        BUILDER.pop();

        BUILDER.push("annihilation");
        ENCH_ANNIHILATION_DAMAGE_PERCENT = BUILDER
                .comment("湮灭：每级额外造成的虚空伤害。默认值：10%")
                .defineInRange("annihilation_damage_percent_per_level", 10.0, 0.0, 1000.0);
        ENCH_ANNIHILATION_MAX_LEVEL = BUILDER
                .comment("湮灭：附魔的最大等级。默认值：3")
                .defineInRange("annihilation_max_level", 3, 1, 10);
        ENCH_ANNIHILATION_ANVIL_MULT = BUILDER
                .comment("湮灭：经验等级乘数。默认值：4")
                .defineInRange("annihilation_cost_multiplier", 4, 1, 100);
        BUILDER.pop();

        BUILDER.push("stability");
        ENCH_STABILITY_RECOIL_REDUCTION_PER_LEVEL = BUILDER
                .comment("稳定：每级降低的后坐力。默认值：15%")
                .defineInRange("stability_recoil_reduction_per_level", 15.0, 0.0, 100.0);
        ENCH_STABILITY_MAX_LEVEL = BUILDER
                .comment("稳定：附魔的最大等级。默认值：5")
                .defineInRange("stability_max_level", 5, 1, 10);
        ENCH_STABILITY_ANVIL_MULT = BUILDER
                .comment("稳定：经验等级乘数。默认值：1")
                .defineInRange("stability_cost_multiplier", 1, 1, 100);
        BUILDER.pop();

        BUILDER.push("anti_gravity");
        ENCH_ANTIGRAVITY_MAX_LEVEL = BUILDER
                .comment("反重力：附魔的最大等级。默认值：1")
                .defineInRange("anti_gravity_max_level", 1, 1, 10);
        ENCH_ANTIGRAVITY_ANVIL_MULT = BUILDER
                .comment("反重力：经验等级乘数。默认值：4")
                .defineInRange("anti_gravity_cost_multiplier", 4, 1, 100);
        BUILDER.pop();

        BUILDER.push("electromagnetic_coil");
        ENCH_COIL_SPEED_PERCENT = BUILDER
                .comment("电磁线圈：每级提升的子弹速度。默认值：50%")
                .defineInRange("electromagnetic_coil_speed_percent", 50.0, 0.0, 1000.0);
        ENCH_COIL_SPREAD_REDUCTION_PERCENT = BUILDER
                .comment("电磁线圈：每级降低的散布%。默认值：25%")
                .defineInRange("electromagnetic_coil_spread_reduction_percent", 25.0, 0.0, 100.0);
        ENCH_COIL_LIGHTNING_CHANCE_PERCENT = BUILDER
                .comment("电磁线圈：命中实体触发雷电伤害的概率。默认值：5%")
                .defineInRange("electromagnetic_coil_lightning_chance_percent", 5.0, 0.0, 100.0);
        ENCH_COIL_LIGHTNING_DAMAGE_PER_LEVEL = BUILDER
                .comment("电磁线圈：触发时每级造成的雷电伤害值。默认值：4")
                .defineInRange("electromagnetic_coil_lightning_damage_per_level", 4.0, 0.0, 1000.0);
        ENCH_COIL_MAX_LEVEL = BUILDER
                .comment("电磁线圈：附魔的最大等级。默认值：2")
                .defineInRange("electromagnetic_coil_max_level", 2, 1, 10);
        ENCH_COIL_ANVIL_MULT = BUILDER
                .comment("电磁线圈：经验等级乘数。默认值：2")
                .defineInRange("electromagnetic_coil_cost_multiplier", 2, 1, 100);
        BUILDER.pop();

        BUILDER.push("standard_ammo");
        ENCH_STANDARD_AMMO_MAX_LEVEL = BUILDER
                .comment("标准弹药：附魔的最大等级。默认值：1")
                .defineInRange("standard_ammo_max_level", 1, 1, 10);
        ENCH_STANDARD_AMMO_ANVIL_MULT = BUILDER
                .comment("标准弹药：经验等级乘数。默认值：4")
                .defineInRange("standard_ammo_cost_multiplier", 4, 1, 100);
        BUILDER.pop();

        BUILDER.push("neurotoxin");
        ENCH_NEUROTOXIN_MAX_LEVEL = BUILDER
                .comment("神经毒素：附魔的最大等级。默认值：2")
                .defineInRange("neurotoxin_max_level", 2, 1, 10);
        ENCH_NEUROTOXIN_TRIGGER_CHANCE_PERCENT_PER_LEVEL = BUILDER
                .comment("神经毒素：每级命中时施加缓慢/失明/中毒的概率。默认值：10%")
                .defineInRange("neurotoxin_trigger_chance_percent_per_level", 10.0, 0.0, 100.0);
        ENCH_NEUROTOXIN_DURATION_TICKS = BUILDER
                .comment("神经毒素：施加状态效果的时长。默认值：100tick")
                .defineInRange("neurotoxin_duration_ticks", 100, 1, 12000);
        ENCH_NEUROTOXIN_ANVIL_MULT = BUILDER
                .comment("神经毒素：经验等级乘数。默认值：4")
                .defineInRange("neurotoxin_cost_multiplier", 4, 1, 100);
        BUILDER.pop();

        BUILDER.push("chain_explosion");
        ENCH_CHAIN_EXPLOSION_MAX_LEVEL = BUILDER
                .comment("连锁爆破：附魔的最大等级。默认值：5")
                .defineInRange("chain_explosion_max_level", 5, 1, 10);
        ENCH_CHAIN_EXPLOSION_BASE_CHANCE_PERCENT = BUILDER
                .comment("连锁爆破：基础触发概率。默认值：10%")
                .defineInRange("chain_explosion_base_chance_percent", 10.0, 0.0, 100.0);
        ENCH_CHAIN_EXPLOSION_CHANCE_PERCENT_PER_LEVEL = BUILDER
                .comment("连锁爆破：每级增加的触发概率。默认值：4%")
                .defineInRange("chain_explosion_chance_percent_per_level", 4.0, 0.0, 100.0);
ENCH_CHAIN_EXPLOSION_RADIUS_MIN = BUILDER
                .comment("连锁爆破：爆炸范围的最小值。默认值：0.5")
                .defineInRange("chain_explosion_radius_min", 0.5, 0.0, 100.0);
        ENCH_CHAIN_EXPLOSION_RADIUS_SCALE_PER_LEVEL = BUILDER
                .comment("连锁爆破：爆炸范围每级的最大值。默认值：1")
                .defineInRange("chain_explosion_radius_scale_per_level", 1.0, 0.0, 100.0);
        ENCH_CHAIN_EXPLOSION_DAMAGE_BASE = BUILDER
                .comment("连锁爆破：爆炸伤害的最小值。默认值：5")
                .defineInRange("chain_explosion_damage_base", 5.0, 0.0, 1000.0);
        ENCH_CHAIN_EXPLOSION_DAMAGE_SCALE_PER_LEVEL = BUILDER
                .comment("连锁爆破：爆炸伤害每级的最大值。默认值：10")
                .defineInRange("chain_explosion_damage_scale_per_level", 10.0, 0.0, 1000.0);
        ENCH_CHAIN_EXPLOSION_ANVIL_MULT = BUILDER
                .comment("连锁爆破：经验等级乘数。默认值：2")
                .defineInRange("chain_explosion_cost_multiplier", 2, 1, 100);
        BUILDER.pop();

        BUILDER.push("preemptive_strike");
        ENCH_PREEMPTIVE_STRIKE_MAX_LEVEL = BUILDER
                .comment("先发制人：附魔的最大等级。默认值：4")
                .defineInRange("preemptive_strike_max_level", 4, 1, 10);
        ENCH_PREEMPTIVE_STRIKE_DAMAGE_PERCENT_PER_LEVEL = BUILDER
                .comment("先发制人：每级提升的伤害。默认值：25%")
                .defineInRange("preemptive_strike_damage_percent_per_level", 25.0, 0.0, 1000.0);
        ENCH_PREEMPTIVE_STRIKE_ANVIL_MULT = BUILDER
                .comment("先发制人：经验等级乘数。默认值：2")
                .defineInRange("preemptive_strike_cost_multiplier", 2, 1, 100);
        BUILDER.pop();

        BUILDER.push("collector");
        ENCH_COLLECTOR_MAX_LEVEL = BUILDER
                .comment("收藏家：附魔的最大等级。默认值：3")
                .defineInRange("collector_max_level", 3, 1, 10);
        ENCH_COLLECTOR_DAMAGE_PERCENT_PER_LEVEL = BUILDER
                .comment("收藏家：每级每个其他附魔增加的子弹伤害。默认值：5%")
                .defineInRange("collector_damage_percent_per_level", 5.0, 0.0, 100.0);
        ENCH_COLLECTOR_ANVIL_MULT = BUILDER
                .comment("收藏家：经验等级乘数。默认值：4")
                .defineInRange("collector_cost_multiplier", 4, 1, 100);
        BUILDER.pop();

        BUILDER.push("explosion_expert");
        ENCH_EXPLOSION_EXPERT_MAX_LEVEL = BUILDER
                .comment("爆破专家：附魔的最大等级。默认值：3")
                .defineInRange("explosion_expert_max_level", 3, 1, 10);
        ENCH_EXPLOSION_EXPERT_ANVIL_MULT = BUILDER
                .comment("爆破专家：经验等级乘数。默认值：4")
                .defineInRange("explosion_expert_cost_multiplier", 4, 1, 100);
        BUILDER.pop();

        BUILDER.push("life_leech");
        ENCH_LIFE_LEECH_MAX_LEVEL = BUILDER
                .comment("生命汲取：附魔的最大等级。默认值：2")
                .defineInRange("life_leech_max_level", 2, 1, 10);
        ENCH_LIFE_LEECH_HEAL_PERCENT_PER_LEVEL = BUILDER
                .comment("生命汲取：击杀实体回复生命值的量。默认值：10%")
                .defineInRange("life_leech_heal_percent_per_level", 10.0, 0.0, 100.0);
        ENCH_LIFE_LEECH_ANVIL_MULT = BUILDER
                .comment("生命汲取：经验等级乘数。默认值：4")
                .defineInRange("life_leech_cost_multiplier", 4, 1, 100);
        BUILDER.pop();

        BUILDER.push("sniper_elite");
        ENCH_SNIPER_ELITE_MAX_LEVEL = BUILDER
                .comment("狙击精英：附魔的最大等级。默认值：4")
                .defineInRange("sniper_elite_max_level", 4, 1, 10);
        ENCH_SNIPER_ELITE_HEADSHOT_PERCENT_PER_LEVEL = BUILDER
                .comment("狙击精英：每级增加的爆头伤害。默认值：25%")
                .defineInRange("sniper_elite_headshot_percent_per_level", 25.0, 0.0, 1000.0);
        ENCH_SNIPER_ELITE_ANVIL_MULT = BUILDER
                .comment("狙击精英：经验等级乘数。默认值：2")
                .defineInRange("sniper_elite_cost_multiplier", 2, 1, 100);
        BUILDER.pop();

        BUILDER.push("pandora_paradox");
ENCH_PANDORA_PARADOX_MAX_LEVEL = BUILDER
                .comment("潘多拉悖论：附魔的最大等级。默认值：1")
                .defineInRange("pandora_paradox_max_level", 1, 1, 10);
        ENCH_PANDORA_PARADOX_ANVIL_MULT = BUILDER
                .comment("潘多拉悖论：经验等级乘数。默认值：8")
                .defineInRange("pandora_paradox_cost_multiplier", 8, 1, 100);
        BUILDER.pop();

        BUILDER.push("smart_scope");
        ENCH_SMART_SCOPE_MAX_LEVEL = BUILDER
                .comment("智能瞄具：附魔的最大等级。默认值：1")
                .defineInRange("smart_scope_max_level", 1, 1, 10);
        ENCH_SMART_SCOPE_MAX_DISTANCE = BUILDER
                .comment("智能瞄具：准星射线检测实体的最大距离。默认值：200")
                .defineInRange("smart_scope_max_distance", 200.0, 1.0, 1000.0);
        ENCH_SMART_SCOPE_ANVIL_MULT = BUILDER
                .comment("智能瞄具：经验等级乘数。默认值：4")
                .defineInRange("smart_scope_cost_multiplier", 4, 1, 100);
        BUILDER.pop();

        BUILDER.push("deep_learning");
        ENCH_DEEP_LEARNING_MAX_LEVEL = BUILDER
                .comment("深度学习：附魔的最大等级。默认值：5")
                .defineInRange("deep_learning_max_level", 5, 1, 10);
        ENCH_DEEP_LEARNING_DAMAGE_PERCENT_PER_GUN_LEVEL_PER_LEVEL = BUILDER
                .comment("深度学习：枪械每有 1 级经验等级，每级附魔增加的子弹伤害。默认值：0.2%")
                .defineInRange("deep_learning_damage_percent_per_gun_level_per_level", 0.2, 0.0, 100.0);
        ENCH_DEEP_LEARNING_ANVIL_MULT = BUILDER
                .comment("深度学习：经验等级乘数。默认值：1")
                .defineInRange("deep_learning_cost_multiplier", 1, 1, 100);
        BUILDER.pop();

        BUILDER.push("equalizer");
        ENCH_EQUALIZER_MAX_LEVEL = BUILDER
                .comment("众生平等：附魔的最大等级。默认值：3")
                .defineInRange("equalizer_max_level", 3, 1, 10);
        ENCH_EQUALIZER_TRIGGER_CHANCE_PERCENT_PER_LEVEL = BUILDER
                .comment("众生平等：子弹命中实体时每级触发的概率。默认值：5%")
                .defineInRange("equalizer_trigger_chance_percent_per_level", 5.0, 0.0, 100.0);
        ENCH_EQUALIZER_ANVIL_MULT = BUILDER
                .comment("众生平等：经验等级乘数。默认值：4")
                .defineInRange("equalizer_cost_multiplier", 4, 1, 100);
        BUILDER.pop();

        BUILDER.push("random");
        ENCH_RANDOM_MAX_LEVEL = BUILDER
                .comment("随机：附魔的最大等级。默认值：4")
                .defineInRange("random_max_level", 4, 1, 10);
        ENCH_RANDOM_EFFECT_CHANCE_PERCENT_PER_LEVEL = BUILDER
                .comment("随机：子弹命中实体时每级施加随机原版状态效果的概率。默认值：7%")
                .defineInRange("random_effect_chance_percent_per_level", 7.0, 0.0, 100.0);
        ENCH_RANDOM_EFFECT_DURATION_TICKS = BUILDER
                .comment("随机：状态效果的持续时长。默认值：140tick")
                .defineInRange("random_effect_duration_ticks", 140, 1, 12000);
        ENCH_RANDOM_ANVIL_MULT = BUILDER
                .comment("随机：经验等级乘数。默认值：2")
                .defineInRange("random_cost_multiplier", 2, 1, 100);
        BUILDER.pop();

        BUILDER.push("decapitation");
        ENCH_DECAPITATION_MAX_LEVEL = BUILDER
                .comment("枭首：附魔的最大等级。默认值：2")
                .defineInRange("decapitation_max_level", 2, 1, 10);
        ENCH_DECAPITATION_HEADSHOT_BONUS_PERCENT_PER_LEVEL = BUILDER
                .comment("枭首：每级每次爆头累积的伤害加成。默认值：5%")
                .defineInRange("decapitation_headshot_bonus_percent_per_level", 5.0, 0.0, 100.0);
        ENCH_DECAPITATION_ANVIL_MULT = BUILDER
                .comment("枭首：经验等级乘数。默认值：2")
                .defineInRange("decapitation_cost_multiplier", 2, 1, 100);
        BUILDER.pop();

        BUILDER.push("charge");
        ENCH_CHARGE_MAX_LEVEL = BUILDER
                .comment("冲锋：附魔的最大等级。默认值：5")
                .defineInRange("charge_max_level", 5, 1, 10);
        ENCH_CHARGE_DAMAGE_PERCENT_PER_SPEED_PER_LEVEL = BUILDER
                .comment("冲锋：每级每有 1m/s 移动速度增加的子弹伤害。默认值：1%")
                .defineInRange("charge_damage_percent_per_speed_per_level", 1.0, 0.0, 1000.0);
        ENCH_CHARGE_ANVIL_MULT = BUILDER
                .comment("冲锋：经验等级乘数。默认值：1")
                .defineInRange("charge_cost_multiplier", 1, 1, 100);
        BUILDER.pop();

        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
