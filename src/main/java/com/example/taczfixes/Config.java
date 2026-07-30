package com.example.taczfixes;

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
    public static final ForgeConfigSpec.BooleanValue PARCOOL_SLIDE_AS_MOVE_INACCURACY;
    public static final ForgeConfigSpec.BooleanValue AUTO_AIM_WHEN_PEEKING;
    public static final ForgeConfigSpec.BooleanValue ADS_INTERRUPT_SPRINT;
    public static final ForgeConfigSpec.BooleanValue FIRE_INTERRUPT_SPRINT;
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
    public static final ForgeConfigSpec.DoubleValue BULLET_RICOCHET_REFLECT_ANGLE_RATIO;
    public static final ForgeConfigSpec.BooleanValue RECOIL_KNOCKBACK_ENABLED;
    public static final ForgeConfigSpec.DoubleValue RECOIL_KNOCKBACK_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue RECOIL_KNOCKBACK_SNEAK_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue RECOIL_KNOCKBACK_SEMI_FACTOR;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RECOIL_FIRE_RATE_DISABLED_GUNS;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PENETRATION_BLOCKED_ENTITIES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DAMAGE_REDUCTION_ENTITIES;
    public static final ForgeConfigSpec.BooleanValue DISABLE_HITBOXES;
    public static final ForgeConfigSpec.BooleanValue DISABLE_THIRD_PERSON;

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
        BUILDER.pop();

        BUILDER.push("limb_damage_multiplier");

        BUILDER.push("player");
        LIMB_THRESHOLD_STANDING = BUILDER
                .comment("玩家在站立时，碰撞箱低于此高度的部分将视为四肢。默认值：0.8")
                .defineInRange("limb_threshold_standing", 0.8, 0.0, 1.0);
        LIMB_THRESHOLD_SNEAKING = BUILDER
                .comment("玩家在潜行时，碰撞箱低于此高度的部分将视为四肢。默认值：0.8")
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
                .comment("非首次开火的后坐力倍率。默认值：0.6")
                .defineInRange("factor", 0.6, 0.0, 1.0);
        RECOIL_FIRE_RATE_PAUSE_FACTOR_PITCH = BUILDER
                .comment("首次开火的竖直后坐力倍率。默认值：2.4")
                .defineInRange("pause_factor_pitch", 2.4, 1.0, 10.0);
        RECOIL_FIRE_RATE_PAUSE_FACTOR_YAW = BUILDER
                .comment("首次开火的水平后坐力倍率。默认值：1.2")
                .defineInRange("pause_factor_yaw", 1.2, 1.0, 10.0);
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
        BULLET_RICOCHET_REFLECT_ANGLE_RATIO = BUILDER
                .comment("跳弹时，反射角的余角与入射角的余角之比。默认值：0.5")
                .defineInRange("reflect_angle_ratio", 0.5, 0.0, 1.0);
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

        BUILDER.push("compat");
        DISABLE_ARCANA_MAGNIFICATION_FOR_SIGHT = BUILDER
                .comment("是否在使用非筒状瞄具时禁用镜内放大。默认值：true",
                        "需要TaCZ: Arcana模组。")
                .define("disable_arcana_magnification_for_sight", true);
        PARCOOL_SLIDE_AS_MOVE_INACCURACY = BUILDER
                .comment("使用ParCool模组的滑铲时，应用移动时而非爬行时的腰射散布。默认值：true",
                        "需要ParCool模组。")
                .define("parcool_slide_as_move_inaccuracy", true);
        ADS_INTERRUPT_SPRINT = BUILDER
                .comment("开镜是否强制打断疾跑。默认值：true")
                .define("ads_interrupt_sprint", true);
        FIRE_INTERRUPT_SPRINT = BUILDER
                .comment("开火是否强制打断疾跑。默认值：true")
                .define("fire_interrupt_sprint", true);
        BUILDER.pop();

        BUILDER.push("explosion");
        EXPLOSION_BULLET_ONLY = BUILDER
                .comment("爆炸击退是否仅对子弹实体生效。默认值：true")
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
                .comment("减少特定实体受到tacz子弹伤害的列表。格式: \"entity_id,reduction\"",
                        "例如: \"irons_spellbooks:shield,0.95\" 表示减少95%的伤害")
                .defineList("entities", List.of("irons_spellbooks:shield,0.95"), it -> it instanceof String);
        BUILDER.pop();

        BUILDER.push("debug");
        DISABLE_HITBOXES = BUILDER
                .comment("禁用F3+B实体碰撞箱显示。默认值：false")
                .define("disable_hitboxes", false);
        DISABLE_THIRD_PERSON = BUILDER
                .comment("禁用切换第三人称视角。默认值：false")
                .define("disable_third_person", false);
        BUILDER.pop();

        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
