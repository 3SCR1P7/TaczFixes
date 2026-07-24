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
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RECOIL_FIRE_RATE_DISABLED_GUNS;


    static {
        BUILDER.push("gun_level");
        GUN_LEVEL_MAX_LEVEL = BUILDER
                .comment("Maximum gun level. Default: 500")
                .defineInRange("max_level", 500, 1, 10000);
        GUN_LEVEL_BASE_KILLS = BUILDER
                .comment("Kills needed for level 1. Default: 1")
                .defineInRange("base_kills", 1, 1, 100000);
        GUN_LEVEL_INCREMENT = BUILDER
                .comment("Additional kills required per level. exp(L) = base + inc * L * (L-1) / 2. When inc=0, linear: exp(L) = base * L. Default: 5")
                .defineInRange("level_increment", 5, 0, 100000);
        BUILDER.pop();

        BUILDER.push("limb_damage");

        BUILDER.push("player");
        LIMB_THRESHOLD_STANDING = BUILDER
                .comment("Relative height threshold for limb hit detection when standing. Hits below this fraction of the player's hitbox height count as limb hits. Default: 0.8")
                .defineInRange("limb_threshold_standing", 0.8, 0.0, 1.0);
        LIMB_THRESHOLD_SNEAKING = BUILDER
                .comment("Relative height threshold for limb hit detection when sneaking. Hits below this fraction of the player's hitbox height count as limb hits. Default: 0.6")
                .defineInRange("limb_threshold_sneaking", 0.6, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("gun_types");
        LIMB_FACTOR_DEFAULT = BUILDER
                .comment("Default limb damage multiplier for unknown gun types (fallback). Default: 0.6")
                .defineInRange("default", 0.6, 0.0, 2.0);
        GUN_TYPE_PISTOL = BUILDER
                .comment("Limb damage multiplier for pistols. Default: 0.6")
                .defineInRange("pistol", 0.6, 0.0, 2.0);
        GUN_TYPE_RIFLE = BUILDER
                .comment("Limb damage multiplier for rifles. Default: 0.6")
                .defineInRange("rifle", 0.6, 0.0, 2.0);
        GUN_TYPE_SNIPER = BUILDER
                .comment("Limb damage multiplier for snipers. Default: 0.6")
                .defineInRange("sniper", 0.6, 0.0, 2.0);
        GUN_TYPE_SHOTGUN = BUILDER
                .comment("Limb damage multiplier for shotguns. Default: 0.6")
                .defineInRange("shotgun", 0.6, 0.0, 2.0);
        GUN_TYPE_SMG = BUILDER
                .comment("Limb damage multiplier for SMGs. Default: 0.6")
                .defineInRange("smg", 0.6, 0.0, 2.0);
        GUN_TYPE_RPG = BUILDER
                .comment("Limb damage multiplier for RPGs. Default: 0.6")
                .defineInRange("rpg", 0.6, 0.0, 2.0);
        GUN_TYPE_MG = BUILDER
                .comment("Limb damage multiplier for machine guns. Default: 0.6")
                .defineInRange("mg", 0.6, 0.0, 2.0);
        GUN_TYPE_OTHER = BUILDER
                .comment("Limb damage multiplier for other/custom gun types. Default: 0.6")
                .defineInRange("other", 0.6, 0.0, 2.0);
        BUILDER.pop();

        BUILDER.push("mobs");
        LIVING_ENTITY_LIMB_ENABLED = BUILDER
                .comment("Enable limb damage for non-player living entities (mobs, animals, etc.)")
                .define("enabled", true);
        LIVING_ENTITY_LIMB_THRESHOLD = BUILDER
                .comment("The bottom fraction of the hitbox that counts as limbs. Default: 0.4 (lower 40%)")
                .defineInRange("threshold", 0.4, 0.0, 1.0);
        LIVING_ENTITY_LIMB_EXCLUDED = BUILDER
                .comment("Entity registry names excluded from limb damage. Format: \"namespace:path\"",
                        "Example: [\"minecraft:ender_dragon\"]")
                .defineList("excluded", List.of("minecraft:ender_dragon"), it -> it instanceof String);
        BUILDER.pop();

        BUILDER.pop();

        BUILDER.push("recoil_fire_rate");
        RECOIL_FIRE_RATE_REDUCTION_ENABLED = BUILDER
                .comment("Enable recoil reduction when firing within a short time window (rapid fire). Compatible with high fire rate guns.")
                .define("enabled", true);
        RECOIL_FIRE_RATE_WINDOW = BUILDER
                .comment("Time window in milliseconds. If the next shot is fired within this window, recoil reduction is applied. Default: 200")
                .defineInRange("window_ms", 200, 0, 10000);
        RECOIL_FIRE_RATE_FACTOR = BUILDER
                .comment("Recoil multiplier when firing within the window (rapid fire). 0.5 = 50% recoil, 0.3 = 30% recoil, 1.0 = no reduction. Default: 0.5")
                .defineInRange("factor", 0.6, 0.0, 1.0);
        RECOIL_FIRE_RATE_PAUSE_FACTOR_PITCH = BUILDER
                .comment("Vertical (pitch) recoil multiplier when NOT firing within the window (paused/between bursts). 2.5 = 250% recoil. Default: 2.4")
                .defineInRange("pause_factor_pitch", 2.4, 1.0, 10.0);
        RECOIL_FIRE_RATE_PAUSE_FACTOR_YAW = BUILDER
                .comment("Horizontal (yaw) recoil multiplier when NOT firing within the window (paused/between bursts). 2.5 = 250% recoil. Default: 1.2")
                .defineInRange("pause_factor_yaw", 1.2, 1.0, 10.0);
        RECOIL_FIRE_RATE_MIN_RPM = BUILDER
                .comment("Minimum RPM (rounds per minute) required for this feature to activate. Guns with RPM below this threshold are unaffected. Default: 300")
                .defineInRange("min_rpm", 300, 0, 1200);
        RECOIL_FIRE_RATE_DISABLED_GUNS = BUILDER
                .comment("List of gun IDs that are exempt from Recoil Fire Rate reduction. Format: \"namespace:path\"",
                        "Example: [\"rfp:m2hb\", \"rfp:dshkm\"]")
                .defineList("disabled_guns", List.of("rfp:m2hb", "rfp:dshkm"), it -> it instanceof String);
        BUILDER.pop();

        BUILDER.push("spread_ramp");
        SPREAD_RAMP_INCREMENT = BUILDER
                .comment("Each shot adds this fraction of the gun's base spread (e.g. 0.03 = +3% per shot). Default: 0.03")
                .defineInRange("increment", 0.03, 0.0, 1.0);
        SPREAD_RAMP_FLAT_INCREMENT = BUILDER
                .comment("Each shot adds this fixed amount to spread (e.g. 0.01 adds 0.01 degrees of spread per stack). Default: 0.02")
                .defineInRange("flat_increment", 0.02, 0.0, 10.0);
        SPREAD_RAMP_MAX_STACKS = BUILDER
                .comment("Maximum number of consecutive shots before spread stops increasing. Default: 30")
                .defineInRange("max_stacks", 30, 0, 1000);
        SPREAD_RAMP_DECAY_DELAY_MS = BUILDER
                .comment("Time in ms since the last shot before spread starts decreasing. Default: 200")
                .defineInRange("decay_delay_ms", 200, 0, 10000);
        SPREAD_RAMP_DECAY = BUILDER
                .comment("Amount of spread recovered per tick when no shot is fired (fraction of base spread, e.g. 0.10 = -10% per tick). Default: 0.10")
                .defineInRange("decay", 0.10, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("bullet_ricochet");
        BULLET_RICOCHET_ENABLE = BUILDER
                .comment("Enable bullet ricochet off blocks with configured tags.")
                .define("enable", true);
        BULLET_RICOCHET_MIN_ANGLE = BUILDER
                .comment("Minimum incidence angle (in degrees) from the surface normal for ricochet to be possible. Below this angle, the bullet will not ricochet. Default: 60")
                .defineInRange("min_angle", 60.0, 0.0, 90.0);
        BULLET_RICOCHET_MAX_ANGLE = BUILDER
                .comment("Incidence angle at which ricochet chance reaches chance_max. Default: 85")
                .defineInRange("max_angle", 90.0, 0.0, 90.0);
        BULLET_RICOCHET_CHANCE_MIN = BUILDER
                .comment("Ricochet probability when incidence angle equals min_angle. Default: 0.0")
                .defineInRange("chance_min", 0.0, 0.0, 1.0);
        BULLET_RICOCHET_CHANCE_MAX = BUILDER
                .comment("Ricochet probability when incidence angle equals or exceeds max_angle. Default: 1.0")
                .defineInRange("chance_max", 1.0, 0.0, 1.0);

        BULLET_RICOCHET_BLOCK_TAGS = BUILDER
                .comment("List of block tags that bullets can ricochet off. Format: \"namespace:path\"",
                        "Example: [\"minecraft:mineable/pickaxe\"]")
                .defineList("block_tags", List.of("minecraft:mineable/pickaxe"), it -> it instanceof String);
        BULLET_RICOCHET_DISABLED_GUNS = BUILDER
                .comment("List of gun IDs that cannot ricochet. Format: \"namespace:path\"",
                        "Example: [\"ts:c4\"]")
                .defineList("disabled_guns", List.of("ts:c4"), it -> it instanceof String);
        BULLET_RICOCHET_DAMAGE_MULTIPLIER = BUILDER
                .comment("Damage multiplier for ricocheted bullets. 0.2 = 20% damage. Default: 0.2")
                .defineInRange("damage_multiplier", 0.2, 0.0, 1.0);
        BULLET_RICOCHET_REFLECT_ANGLE_RATIO = BUILDER
                .comment("Ratio of reflection angle to incidence angle. 0.5 means the bullet reflects at half the incidence angle. Default: 0.5")
                .defineInRange("reflect_angle_ratio", 0.5, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("recoil_knockback");
        RECOIL_KNOCKBACK_ENABLED = BUILDER
                .comment("Apply a knockback force to the player opposite to their aim direction when firing.")
                .define("enable", true);
        RECOIL_KNOCKBACK_MULTIPLIER = BUILDER
                .comment("Multiplier for recoil knockback force. Higher values push the player back more. Default: 0.015")
                .defineInRange("multiplier", 0.015, 0.0, 1.0);
        RECOIL_KNOCKBACK_SNEAK_MULTIPLIER = BUILDER
                .comment("Multiplier applied to recoil knockback force when sneaking. Default: 0.5")
                .defineInRange("sneak_multiplier", 0.5, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("misc");
        BURST_BLOCK_ATTACHMENTS = BUILDER
                .comment("List of attachment IDs that prevent burst fire. When a gun has any of these attachments equipped, burst mode will be disabled and only a dry fire sound will play.",
                        "Example: [\"ccrp:ammo_mod_hap\"]")
                .defineList("burst_block_attachments", List.of("ccrp:ammo_mod_hap"), it -> it instanceof String);
        BUILDER.pop();

        BUILDER.push("compat");
        DISABLE_ARCANA_MAGNIFICATION_FOR_SIGHT = BUILDER
                .comment("When using a sight-type scope attachment (red dot, holo, etc.), disable TACZ Arcana's scope-in magnification so it behaves like a non-magnifying sight.",
                        "Only applies if TACZ Arcana is installed.")
                .define("disable_arcana_magnification_for_sight", true);
        ADS_INTERRUPT_SPRINT = BUILDER
                .comment("Stop sprinting when aiming down sights. Fixes animation conflicts with mods like Parcool that add first-person sprint animations.")
                .define("ads_interrupt_sprint", true);
        FIRE_INTERRUPT_SPRINT = BUILDER
                .comment("Stop sprinting when firing a gun. Prevents shooting while sprinting.")
                .define("fire_interrupt_sprint", true);
        BUILDER.pop();

        BUILDER.push("explosion");
        EXPLOSION_BULLET_ONLY = BUILDER
                .comment("When enabled, bullet explosions only affect tacz:bullet entities (EntityKineticBullet), ignoring all other entities (players, mobs, etc.).")
                .define("bullet_only", false);
        BUILDER.pop();

        BUILDER.push("peek_aim");
        AUTO_AIM_WHEN_PEEKING = BUILDER
                .comment("Automatically aim down sights when using gd656peek's left/right peek while holding a TACZ gun.")
                .define("auto_aim_when_peeking", false);
        BUILDER.pop();

        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
