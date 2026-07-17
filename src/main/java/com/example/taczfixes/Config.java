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
    public static final ForgeConfigSpec.BooleanValue AUTO_AIM_WHEN_PEEKING;
    public static final ForgeConfigSpec.BooleanValue ADS_INTERRUPT_SPRINT;
    public static final ForgeConfigSpec.BooleanValue RECOIL_FIRE_RATE_REDUCTION_ENABLED;
    public static final ForgeConfigSpec.IntValue RECOIL_FIRE_RATE_WINDOW;
    public static final ForgeConfigSpec.DoubleValue RECOIL_FIRE_RATE_FACTOR;
    public static final ForgeConfigSpec.DoubleValue RECOIL_FIRE_RATE_PAUSE_FACTOR_PITCH;
    public static final ForgeConfigSpec.DoubleValue RECOIL_FIRE_RATE_PAUSE_FACTOR_YAW;
    public static final ForgeConfigSpec.IntValue RECOIL_FIRE_RATE_MIN_RPM;

    static {
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

BUILDER.push("burst_fire");
        BURST_BLOCK_ATTACHMENTS = BUILDER
                .comment("List of attachment IDs that prevent burst fire. When a gun has any of these attachments equipped, burst mode will be disabled and only a dry fire sound will play.",
                        "Example: [\"ccrp:ammo_mod_hap\"]")
                .defineList("burst_block_attachments", List.of("ccrp:ammo_mod_hap"), it -> it instanceof String);
        BUILDER.pop();

        BUILDER.push("recoil_fire_rate");
        RECOIL_FIRE_RATE_REDUCTION_ENABLED = BUILDER
                .comment("Enable recoil reduction when firing within a short time window (rapid fire). Compatible with high fire rate guns.")
                .define("enabled", true);
        RECOIL_FIRE_RATE_WINDOW = BUILDER
                .comment("Time window in milliseconds. If the next shot is fired within this window, recoil reduction is applied. Default: 200")
                .defineInRange("window_ms", 200, 0, 5000);
        RECOIL_FIRE_RATE_FACTOR = BUILDER
                .comment("Recoil multiplier when firing within the window (rapid fire). 0.5 = 50% recoil, 0.3 = 30% recoil, 1.0 = no reduction. Default: 0.5")
                .defineInRange("factor", 0.6, 0.0, 1.0);
        RECOIL_FIRE_RATE_PAUSE_FACTOR_PITCH = BUILDER
                .comment("Vertical (pitch) recoil multiplier when NOT firing within the window (paused/between bursts). 2.5 = 250% recoil. Default: 2.4")
                .defineInRange("pause_factor_pitch", 2.4, 1.0, 10.0);
        RECOIL_FIRE_RATE_PAUSE_FACTOR_YAW = BUILDER
                .comment("Horizontal (yaw) recoil multiplier when NOT firing within the window (paused/between bursts). 2.5 = 250% recoil. Default: 2.4")
                .defineInRange("pause_factor_yaw", 1.2, 1.0, 10.0);
        RECOIL_FIRE_RATE_MIN_RPM = BUILDER
                .comment("Minimum RPM (rounds per minute) required for this feature to activate. Guns with RPM below this threshold are unaffected. Default: 300")
                .defineInRange("min_rpm", 300, 0, 2400);
        BUILDER.pop();

        BUILDER.push("peek_aim");
        AUTO_AIM_WHEN_PEEKING = BUILDER
                .comment("Automatically aim down sights when using gd656peek's left/right peek while holding a TACZ gun.")
                .define("auto_aim_when_peeking", true);
        BUILDER.pop();

        BUILDER.push("compat");
        ADS_INTERRUPT_SPRINT = BUILDER
                .comment("Stop sprinting when aiming down sights. Fixes animation conflicts with mods like Parcool that add first-person sprint animations.")
                .define("ads_interrupt_sprint", true);
        BUILDER.pop();

    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
