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
                .comment("Default limb damage multiplier for unknown gun types (fallback). Default: 0.65")
                .defineInRange("default", 0.65, 0.0, 2.0);
        GUN_TYPE_PISTOL = BUILDER
                .comment("Limb damage multiplier for pistols. Default: 0.65")
                .defineInRange("pistol", 0.65, 0.0, 2.0);
        GUN_TYPE_RIFLE = BUILDER
                .comment("Limb damage multiplier for rifles. Default: 0.65")
                .defineInRange("rifle", 0.65, 0.0, 2.0);
        GUN_TYPE_SNIPER = BUILDER
                .comment("Limb damage multiplier for snipers. Default: 0.65")
                .defineInRange("sniper", 0.65, 0.0, 2.0);
        GUN_TYPE_SHOTGUN = BUILDER
                .comment("Limb damage multiplier for shotguns. Default: 0.65")
                .defineInRange("shotgun", 0.65, 0.0, 2.0);
        GUN_TYPE_SMG = BUILDER
                .comment("Limb damage multiplier for SMGs. Default: 0.65")
                .defineInRange("smg", 0.65, 0.0, 2.0);
        GUN_TYPE_RPG = BUILDER
                .comment("Limb damage multiplier for RPGs. Default: 0.65")
                .defineInRange("rpg", 0.65, 0.0, 2.0);
        GUN_TYPE_MG = BUILDER
                .comment("Limb damage multiplier for machine guns. Default: 0.65")
                .defineInRange("mg", 0.65, 0.0, 2.0);
        GUN_TYPE_OTHER = BUILDER
                .comment("Limb damage multiplier for other/custom gun types. Default: 0.65")
                .defineInRange("other", 0.65, 0.0, 2.0);
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
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
