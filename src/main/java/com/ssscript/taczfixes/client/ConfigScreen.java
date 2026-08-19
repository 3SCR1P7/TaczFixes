package com.ssscript.taczfixes.client;

import com.ssscript.taczfixes.common.Config;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class ConfigScreen {
    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.taczfixes.title"))
                .setSavingRunnable(() -> Config.SPEC.save());
        ConfigEntryBuilder entry = builder.entryBuilder();

        buildGunLevel(builder.getOrCreateCategory(cat("gun_level")), entry);
        buildSteplessZoom(builder.getOrCreateCategory(cat("stepless_zoom")), entry);
        buildLimbDamage(builder.getOrCreateCategory(cat("limb_damage")), entry);
        buildRecoilModifier(builder.getOrCreateCategory(cat("recoil_modifier")), entry);
        buildInaccuracy(builder.getOrCreateCategory(cat("inaccuracy")), entry);
        buildRicochet(builder.getOrCreateCategory(cat("ricochet")), entry);
        buildRecoilKnockback(builder.getOrCreateCategory(cat("recoil_knockback")), entry);
        buildMisc(builder.getOrCreateCategory(cat("misc")), entry);
        buildCompat(builder.getOrCreateCategory(cat("compat")), entry);
        buildEnchantment(builder.getOrCreateCategory(cat("enchantment")), entry);

        return builder.build();
    }

    private static Component cat(String slug) {
        return Component.translatable("config.taczfixes.category." + slug);
    }

    private static Component entry(String slug) {
        return Component.translatable("config.taczfixes.entry." + slug);
    }

    private static void buildGunLevel(ConfigCategory cat, ConfigEntryBuilder entry) {
        int_(cat, entry, "gun_level.max_level", Config.GUN_LEVEL_MAX_LEVEL);
        int_(cat, entry, "gun_level.base_kills", Config.GUN_LEVEL_BASE_KILLS);
        int_(cat, entry, "gun_level.increment", Config.GUN_LEVEL_INCREMENT);
        bool_(cat, entry, "gun_level.bottle_enabled", Config.GUN_BOTTLE_ENABLED);
        int_(cat, entry, "gun_level.bottle_exp_per_bottle", Config.GUN_BOTTLE_EXP_PER_BOTTLE);
        int_(cat, entry, "gun_level.bottle_cost", Config.GUN_BOTTLE_COST);
    }

    private static void buildSteplessZoom(ConfigCategory cat, ConfigEntryBuilder entry) {
        bool_(cat, entry, "stepless_zoom.enabled", Config.STEPLESS_ZOOM_ENABLED);
        dbl_(cat, entry, "stepless_zoom.ctrl_multiplier", Config.STEPLESS_ZOOM_CTRL_MULTIPLIER);
        dbl_(cat, entry, "stepless_zoom.alt_multiplier", Config.STEPLESS_ZOOM_ALT_MULTIPLIER);
    }

    private static void buildLimbDamage(ConfigCategory cat, ConfigEntryBuilder entry) {
        List<AbstractConfigListEntry> player = new ArrayList<>();
        dbl_(player, entry, "limb_damage.threshold_standing", Config.LIMB_THRESHOLD_STANDING);
        dbl_(player, entry, "limb_damage.threshold_sneaking", Config.LIMB_THRESHOLD_SNEAKING);
        cat.addEntry(entry.startSubCategory(cat("player"), player).build());

        List<AbstractConfigListEntry> gunTypes = new ArrayList<>();
        dbl_(gunTypes, entry, "limb_damage.factor_default", Config.LIMB_FACTOR_DEFAULT);
        dbl_(gunTypes, entry, "limb_damage.factor_pistol", Config.GUN_TYPE_PISTOL);
        dbl_(gunTypes, entry, "limb_damage.factor_rifle", Config.GUN_TYPE_RIFLE);
        dbl_(gunTypes, entry, "limb_damage.factor_sniper", Config.GUN_TYPE_SNIPER);
        dbl_(gunTypes, entry, "limb_damage.factor_shotgun", Config.GUN_TYPE_SHOTGUN);
        dbl_(gunTypes, entry, "limb_damage.factor_smg", Config.GUN_TYPE_SMG);
        dbl_(gunTypes, entry, "limb_damage.factor_rpg", Config.GUN_TYPE_RPG);
        dbl_(gunTypes, entry, "limb_damage.factor_mg", Config.GUN_TYPE_MG);
        dbl_(gunTypes, entry, "limb_damage.factor_other", Config.GUN_TYPE_OTHER);
        cat.addEntry(entry.startSubCategory(cat("gun_types"), gunTypes).build());

        List<AbstractConfigListEntry> mobs = new ArrayList<>();
        bool_(mobs, entry, "limb_damage.living_entity_enabled", Config.LIVING_ENTITY_LIMB_ENABLED);
        dbl_(mobs, entry, "limb_damage.living_entity_threshold", Config.LIVING_ENTITY_LIMB_THRESHOLD);
        lst_(mobs, entry, "limb_damage.living_entity_excluded", Config.LIVING_ENTITY_LIMB_EXCLUDED);
        cat.addEntry(entry.startSubCategory(cat("mobs"), mobs).build());
    }

    private static void buildRecoilModifier(ConfigCategory cat, ConfigEntryBuilder entry) {
        bool_(cat, entry, "recoil_modifier.enabled", Config.RECOIL_FIRE_RATE_REDUCTION_ENABLED);
        int_(cat, entry, "recoil_modifier.window_ms", Config.RECOIL_FIRE_RATE_WINDOW);
        dbl_(cat, entry, "recoil_modifier.factor", Config.RECOIL_FIRE_RATE_FACTOR);
        dbl_(cat, entry, "recoil_modifier.pause_factor_pitch", Config.RECOIL_FIRE_RATE_PAUSE_FACTOR_PITCH);
        dbl_(cat, entry, "recoil_modifier.pause_factor_yaw", Config.RECOIL_FIRE_RATE_PAUSE_FACTOR_YAW);
        int_(cat, entry, "recoil_modifier.min_rpm", Config.RECOIL_FIRE_RATE_MIN_RPM);
        lst_(cat, entry, "recoil_modifier.disabled_guns", Config.RECOIL_FIRE_RATE_DISABLED_GUNS);
    }

    private static void buildInaccuracy(ConfigCategory cat, ConfigEntryBuilder entry) {
        bool_(cat, entry, "inaccuracy.enabled", Config.SPREAD_RAMP_ENABLED);
        dbl_(cat, entry, "inaccuracy.ramp_increment", Config.SPREAD_RAMP_INCREMENT);
        dbl_(cat, entry, "inaccuracy.ramp_flat_increment", Config.SPREAD_RAMP_FLAT_INCREMENT);
        int_(cat, entry, "inaccuracy.ramp_max_stacks", Config.SPREAD_RAMP_MAX_STACKS);
        int_(cat, entry, "inaccuracy.ramp_decay_delay_ms", Config.SPREAD_RAMP_DECAY_DELAY_MS);
        dbl_(cat, entry, "inaccuracy.ramp_decay", Config.SPREAD_RAMP_DECAY);
    }

    private static void buildRicochet(ConfigCategory cat, ConfigEntryBuilder entry) {
        bool_(cat, entry, "ricochet.enabled", Config.BULLET_RICOCHET_ENABLE);
        dbl_(cat, entry, "ricochet.min_angle", Config.BULLET_RICOCHET_MIN_ANGLE);
        dbl_(cat, entry, "ricochet.max_angle", Config.BULLET_RICOCHET_MAX_ANGLE);
        dbl_(cat, entry, "ricochet.chance_min", Config.BULLET_RICOCHET_CHANCE_MIN);
        dbl_(cat, entry, "ricochet.chance_max", Config.BULLET_RICOCHET_CHANCE_MAX);
        lst_(cat, entry, "ricochet.block_tags", Config.BULLET_RICOCHET_BLOCK_TAGS);
        lst_(cat, entry, "ricochet.disabled_guns", Config.BULLET_RICOCHET_DISABLED_GUNS);
        dbl_(cat, entry, "ricochet.damage_multiplier", Config.BULLET_RICOCHET_DAMAGE_MULTIPLIER);
        dbl_(cat, entry, "ricochet.reflect_angle_ratio_min", Config.BULLET_RICOCHET_REFLECT_ANGLE_RATIO_MIN);
        dbl_(cat, entry, "ricochet.reflect_angle_ratio_max", Config.BULLET_RICOCHET_REFLECT_ANGLE_RATIO_MAX);
        bool_(cat, entry, "ricochet.top_bottom_enable", Config.BULLET_RICOCHET_TOP_BOTTOM_ENABLE);
    }

    private static void buildRecoilKnockback(ConfigCategory cat, ConfigEntryBuilder entry) {
        bool_(cat, entry, "recoil_knockback.enabled", Config.RECOIL_KNOCKBACK_ENABLED);
        dbl_(cat, entry, "recoil_knockback.multiplier", Config.RECOIL_KNOCKBACK_MULTIPLIER);
        dbl_(cat, entry, "recoil_knockback.sneak_multiplier", Config.RECOIL_KNOCKBACK_SNEAK_MULTIPLIER);
        dbl_(cat, entry, "recoil_knockback.semi_factor", Config.RECOIL_KNOCKBACK_SEMI_FACTOR);
    }

    private static void buildMisc(ConfigCategory cat, ConfigEntryBuilder entry) {
        List<AbstractConfigListEntry> toast = new ArrayList<>();
        int_(toast, entry, "misc.refit_toast_duration_ms", Config.REFIT_TOAST_DURATION_MS);
        int_(toast, entry, "misc.refit_toast_fade_ms", Config.REFIT_TOAST_FADE_MS);
        cat.addEntry(entry.startSubCategory(cat("refit_toast"), toast).build());

        List<AbstractConfigListEntry> viewZoom = new ArrayList<>();
        dbl_(viewZoom, entry, "misc.refit_view_zoom_min", Config.REFIT_VIEW_ZOOM_MIN);
        dbl_(viewZoom, entry, "misc.refit_view_zoom_max", Config.REFIT_VIEW_ZOOM_MAX);
        cat.addEntry(entry.startSubCategory(cat("refit_view_zoom"), viewZoom).build());

        List<AbstractConfigListEntry> burst = new ArrayList<>();
        lst_(burst, entry, "misc.burst_block_attachments", Config.BURST_BLOCK_ATTACHMENTS);
        cat.addEntry(entry.startSubCategory(cat("burst"), burst).build());

        List<AbstractConfigListEntry> explosion = new ArrayList<>();
        bool_(explosion, entry, "misc.explosion_bullet_only", Config.EXPLOSION_BULLET_ONLY);
        cat.addEntry(entry.startSubCategory(cat("explosion"), explosion).build());

        List<AbstractConfigListEntry> peek = new ArrayList<>();
        bool_(peek, entry, "misc.auto_aim_when_peeking", Config.AUTO_AIM_WHEN_PEEKING);
        cat.addEntry(entry.startSubCategory(cat("peek"), peek).build());

        List<AbstractConfigListEntry> unpie = new ArrayList<>();
        lst_(unpie, entry, "misc.penetration_blocked_entities", Config.PENETRATION_BLOCKED_ENTITIES);
        cat.addEntry(entry.startSubCategory(cat("non_penetrable"), unpie).build());

        List<AbstractConfigListEntry> damage = new ArrayList<>();
        lst_(damage, entry, "misc.damage_reduction_entities", Config.DAMAGE_REDUCTION_ENTITIES);
        bool_(damage, entry, "misc.ignore_entity_enable", Config.BULLET_IGNORE_ENTITY_ENABLE);
        int_(damage, entry, "misc.ignore_entity_cooldown_ms", Config.BULLET_IGNORE_ENTITY_COOLDOWN_MS);
        cat.addEntry(entry.startSubCategory(cat("damage"), damage).build());

        List<AbstractConfigListEntry> debug = new ArrayList<>();
        bool_(debug, entry, "misc.disable_hitboxes", Config.DISABLE_HITBOXES);
        bool_(debug, entry, "misc.disable_third_person", Config.DISABLE_THIRD_PERSON);
        cat.addEntry(entry.startSubCategory(cat("debug"), debug).build());
    }

    private static void buildCompat(ConfigCategory cat, ConfigEntryBuilder entry) {
        bool_(cat, entry, "compat.disable_arcana_magnification_for_sight", Config.DISABLE_ARCANA_MAGNIFICATION_FOR_SIGHT);
        lst_(cat, entry, "compat.hide_particles_in_arcana_thermal", Config.HIDE_PARTICLES_IN_ARCANA_THERMAL);
        bool_(cat, entry, "compat.parcool_slide_as_move_inaccuracy", Config.PARCOOL_SLIDE_AS_MOVE_INACCURACY);
        bool_(cat, entry, "compat.disable_tracking_after_penetration", Config.DISABLE_TRACKING_AFTER_PENETRATION);
        dbl_(cat, entry, "compat.peek_headshot_height", Config.PEEK_HEADSHOT_HEIGHT);
        bool_(cat, entry, "compat.ads_interrupt_sprint", Config.ADS_INTERRUPT_SPRINT);
        bool_(cat, entry, "compat.prevent_sprint_reengage_when_tilt", Config.PREVENT_SPRINT_REENGAGE_WHEN_TILT);
    }

    private static void buildEnchantment(ConfigCategory cat, ConfigEntryBuilder entry) {
        bool_(cat, entry, "enchantment.enabled", Config.GUN_ENCHANTMENT_ENABLED);
        int_(cat, entry, "enchantment.value", Config.GUN_ENCHANTMENT_VALUE);
        lst_(cat, entry, "enchantment.whitelist", Config.GUN_ENCHANT_WHITELIST);
        bool_(cat, entry, "enchantment.ignore_conflict", Config.GUN_ENCHANT_IGNORE_CONFLICT);

        sub(cat, entry, "general", e -> {
            dbl_(e, entry, "enchantment.power_bullet_mult", Config.ENCH_POWER_BULLET_MULT);
            dbl_(e, entry, "enchantment.smite_bullet_mult", Config.ENCH_SMITE_BULLET_MULT);
            dbl_(e, entry, "enchantment.bane_bullet_mult", Config.ENCH_BANE_BULLET_MULT);
            dbl_(e, entry, "enchantment.impaling_bullet_mult", Config.ENCH_IMPALING_BULLET_MULT);
            int_(e, entry, "enchantment.flame_ignite_ticks", Config.ENCH_FLAME_IGNITE_TICKS);
            int_(e, entry, "enchantment.pierce_per_level", Config.ENCH_PIERCE_PER_LEVEL);
            dbl_(e, entry, "enchantment.unbreaking_no_consume_chance", Config.ENCH_UNBREAKING_NO_CONSUME_CHANCE);
            int_(e, entry, "enchantment.mending_ammo_per_kill", Config.ENCH_MENDING_AMMO_PER_KILL);
            dbl_(e, entry, "enchantment.sharpness_melee_damage", Config.ENCH_SHARPNESS_MELEE_DAMAGE);
            dbl_(e, entry, "enchantment.sweeping_distance_mult", Config.ENCH_SWEEPING_DISTANCE_MULT);
            int_(e, entry, "enchantment.fire_aspect_melee_ticks", Config.ENCH_FIRE_ASPECT_MELEE_TICKS);
            dbl_(e, entry, "enchantment.quick_charge_time_reduction", Config.ENCH_QUICK_CHARGE_TIME_REDUCTION);
            dbl_(e, entry, "enchantment.fortune_headshot_chance_percent_per_level", Config.ENCH_FORTUNE_HEADSHOT_CHANCE_PERCENT_PER_LEVEL);
        });
        sub(cat, entry, "punch", e -> {
            dbl_(e, entry, "enchantment.punch_bullet_knockback_mult", Config.ENCH_PUNCH_BULLET_KNOCKBACK_MULT);
            dbl_(e, entry, "enchantment.punch_bullet_knockback_flat", Config.ENCH_PUNCH_BULLET_KNOCKBACK_FLAT);
        });
        sub(cat, entry, "knockback", e -> {
            dbl_(e, entry, "enchantment.knockback_melee_mult", Config.ENCH_KNOCKBACK_MELEE_MULT);
            dbl_(e, entry, "enchantment.knockback_melee_flat", Config.ENCH_KNOCKBACK_MELEE_FLAT);
        });
        sub(cat, entry, "channeling", e -> {
            int_(e, entry, "enchantment.cooldown_ms", Config.ENCH_STEAL_LIGHTNING_COOLDOWN_MS);
            dbl_(e, entry, "enchantment.channeling_trigger_chance", Config.ENCH_CHANNELING_TRIGGER_CHANCE);
            bool_(e, entry, "enchantment.channeling_only_thunder", Config.ENCH_CHANNELING_ONLY_THUNDER);
        });
        sub(cat, entry, "loyalty", e -> {
            int_(e, entry, "enchantment.loyalty_range_per_level", Config.ENCH_LOYALTY_RANGE_PER_LEVEL);
            int_(e, entry, "enchantment.loyalty_angle_per_level", Config.ENCH_LOYALTY_ANGLE_PER_LEVEL);
        });
        sub(cat, entry, "multishot", e -> {
            int_(e, entry, "enchantment.multishot_extra_count", Config.ENCH_MULTISHOT_EXTRA_COUNT);
            dbl_(e, entry, "enchantment.multishot_trigger_chance", Config.ENCH_MULTISHOT_TRIGGER_CHANCE);
            dbl_(e, entry, "enchantment.multishot_spread_angle", Config.ENCH_MULTISHOT_SPREAD_ANGLE);
            int_(e, entry, "enchantment.cooldown_ms", Config.ENCH_MULTISHOT_COOLDOWN_MS);
        });
        sub(cat, entry, "riptide", e -> {
            dbl_(e, entry, "enchantment.riptide_speed_mult", Config.ENCH_RIPTIDE_SPEED_MULT);
            dbl_(e, entry, "enchantment.riptide_damage_mult", Config.ENCH_RIPTIDE_DAMAGE_MULT);
        });
        sub(cat, entry, "efficiency", e -> {
            dbl_(e, entry, "enchantment.efficiency_fire_rate_percent_per_level", Config.ENCH_EFFICIENCY_FIRE_RATE_PERCENT_PER_LEVEL);
            dbl_(e, entry, "enchantment.efficiency_bolt_time_reduction_percent_per_level", Config.ENCH_EFFICIENCY_BOLT_TIME_REDUCTION_PERCENT_PER_LEVEL);
        });
        sub(cat, entry, "overload", e -> {
            dbl_(e, entry, "enchantment.damage_percent_per_level", Config.ENCH_OVERLOAD_DAMAGE_PERCENT);
            int_(e, entry, "enchantment.max_level", Config.ENCH_OVERLOAD_MAX_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_OVERLOAD_ANVIL_MULT);
        });
        sub(cat, entry, "annihilation", e -> {
            dbl_(e, entry, "enchantment.annihilation_damage_percent", Config.ENCH_ANNIHILATION_DAMAGE_PERCENT);
            int_(e, entry, "enchantment.max_level", Config.ENCH_ANNIHILATION_MAX_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_ANNIHILATION_ANVIL_MULT);
        });
        sub(cat, entry, "stability", e -> {
            dbl_(e, entry, "enchantment.stability_recoil_reduction_per_level", Config.ENCH_STABILITY_RECOIL_REDUCTION_PER_LEVEL);
            int_(e, entry, "enchantment.max_level", Config.ENCH_STABILITY_MAX_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_STABILITY_ANVIL_MULT);
        });
        sub(cat, entry, "anti_gravity", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_ANTIGRAVITY_MAX_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_ANTIGRAVITY_ANVIL_MULT);
        });
        sub(cat, entry, "electromagnetic_coil", e -> {
            dbl_(e, entry, "enchantment.coil_speed_percent", Config.ENCH_COIL_SPEED_PERCENT);
            dbl_(e, entry, "enchantment.coil_spread_reduction_percent", Config.ENCH_COIL_SPREAD_REDUCTION_PERCENT);
            dbl_(e, entry, "enchantment.coil_lightning_chance_percent", Config.ENCH_COIL_LIGHTNING_CHANCE_PERCENT);
            dbl_(e, entry, "enchantment.coil_lightning_damage_per_level", Config.ENCH_COIL_LIGHTNING_DAMAGE_PER_LEVEL);
            int_(e, entry, "enchantment.max_level", Config.ENCH_COIL_MAX_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_COIL_ANVIL_MULT);
        });
        sub(cat, entry, "standard_ammo", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_STANDARD_AMMO_MAX_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_STANDARD_AMMO_ANVIL_MULT);
        });
        sub(cat, entry, "neurotoxin", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_NEUROTOXIN_MAX_LEVEL);
            dbl_(e, entry, "enchantment.trigger_chance_percent_per_level", Config.ENCH_NEUROTOXIN_TRIGGER_CHANCE_PERCENT_PER_LEVEL);
            int_(e, entry, "enchantment.effect_duration_ticks", Config.ENCH_NEUROTOXIN_DURATION_TICKS);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_NEUROTOXIN_ANVIL_MULT);
        });
        sub(cat, entry, "chain_explosion", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_CHAIN_EXPLOSION_MAX_LEVEL);
            dbl_(e, entry, "enchantment.chain_explosion_base_chance_percent", Config.ENCH_CHAIN_EXPLOSION_BASE_CHANCE_PERCENT);
            dbl_(e, entry, "enchantment.trigger_chance_percent_per_level", Config.ENCH_CHAIN_EXPLOSION_CHANCE_PERCENT_PER_LEVEL);
            dbl_(e, entry, "enchantment.chain_explosion_radius_min", Config.ENCH_CHAIN_EXPLOSION_RADIUS_MIN);
            dbl_(e, entry, "enchantment.chain_explosion_radius_scale_per_level", Config.ENCH_CHAIN_EXPLOSION_RADIUS_SCALE_PER_LEVEL);
            dbl_(e, entry, "enchantment.chain_explosion_damage_base", Config.ENCH_CHAIN_EXPLOSION_DAMAGE_BASE);
            dbl_(e, entry, "enchantment.chain_explosion_damage_scale_per_level", Config.ENCH_CHAIN_EXPLOSION_DAMAGE_SCALE_PER_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_CHAIN_EXPLOSION_ANVIL_MULT);
        });
        sub(cat, entry, "preemptive_strike", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_PREEMPTIVE_STRIKE_MAX_LEVEL);
            dbl_(e, entry, "enchantment.damage_percent_per_level", Config.ENCH_PREEMPTIVE_STRIKE_DAMAGE_PERCENT_PER_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_PREEMPTIVE_STRIKE_ANVIL_MULT);
        });
        sub(cat, entry, "collector", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_COLLECTOR_MAX_LEVEL);
            dbl_(e, entry, "enchantment.collector_damage_percent_per_level", Config.ENCH_COLLECTOR_DAMAGE_PERCENT_PER_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_COLLECTOR_ANVIL_MULT);
        });
        sub(cat, entry, "explosion_expert", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_EXPLOSION_EXPERT_MAX_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_EXPLOSION_EXPERT_ANVIL_MULT);
        });
        sub(cat, entry, "life_leech", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_LIFE_LEECH_MAX_LEVEL);
            dbl_(e, entry, "enchantment.life_leech_heal_percent_per_level", Config.ENCH_LIFE_LEECH_HEAL_PERCENT_PER_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_LIFE_LEECH_ANVIL_MULT);
        });
        sub(cat, entry, "sniper_elite", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_SNIPER_ELITE_MAX_LEVEL);
            dbl_(e, entry, "enchantment.sniper_elite_headshot_percent_per_level", Config.ENCH_SNIPER_ELITE_HEADSHOT_PERCENT_PER_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_SNIPER_ELITE_ANVIL_MULT);
        });
        sub(cat, entry, "pandora_paradox", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_PANDORA_PARADOX_MAX_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_PANDORA_PARADOX_ANVIL_MULT);
        });
        sub(cat, entry, "smart_scope", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_SMART_SCOPE_MAX_LEVEL);
            dbl_(e, entry, "enchantment.smart_scope_max_distance", Config.ENCH_SMART_SCOPE_MAX_DISTANCE);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_SMART_SCOPE_ANVIL_MULT);
        });
        sub(cat, entry, "deep_learning", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_DEEP_LEARNING_MAX_LEVEL);
            dbl_(e, entry, "enchantment.deep_learning_damage_percent_per_gun_level_per_level", Config.ENCH_DEEP_LEARNING_DAMAGE_PERCENT_PER_GUN_LEVEL_PER_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_DEEP_LEARNING_ANVIL_MULT);
        });
        sub(cat, entry, "equalizer", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_EQUALIZER_MAX_LEVEL);
            dbl_(e, entry, "enchantment.trigger_chance_percent_per_level", Config.ENCH_EQUALIZER_TRIGGER_CHANCE_PERCENT_PER_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_EQUALIZER_ANVIL_MULT);
        });
        sub(cat, entry, "random", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_RANDOM_MAX_LEVEL);
            dbl_(e, entry, "enchantment.trigger_chance_percent_per_level", Config.ENCH_RANDOM_EFFECT_CHANCE_PERCENT_PER_LEVEL);
            int_(e, entry, "enchantment.effect_duration_ticks", Config.ENCH_RANDOM_EFFECT_DURATION_TICKS);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_RANDOM_ANVIL_MULT);
        });
        sub(cat, entry, "decapitation", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_DECAPITATION_MAX_LEVEL);
            dbl_(e, entry, "enchantment.decapitation_headshot_bonus_percent_per_level", Config.ENCH_DECAPITATION_HEADSHOT_BONUS_PERCENT_PER_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_DECAPITATION_ANVIL_MULT);
        });
        sub(cat, entry, "charge", e -> {
            int_(e, entry, "enchantment.max_level", Config.ENCH_CHARGE_MAX_LEVEL);
            dbl_(e, entry, "enchantment.charge_damage_percent_per_speed_per_level", Config.ENCH_CHARGE_DAMAGE_PERCENT_PER_SPEED_PER_LEVEL);
            int_(e, entry, "enchantment.anvil_mult", Config.ENCH_CHARGE_ANVIL_MULT);
        });
    }

    private interface EntryAdder {
        void add(List<AbstractConfigListEntry> entries);
    }

    private static void sub(ConfigCategory cat, ConfigEntryBuilder entry, String slug, EntryAdder adder) {
        List<AbstractConfigListEntry> entries = new ArrayList<>();
        adder.add(entries);
        cat.addEntry(entry.startSubCategory(cat(slug), entries).build());
    }

    private static void bool_(ConfigCategory cat, ConfigEntryBuilder entry, String slug, ForgeConfigSpec.BooleanValue v) {
        cat.addEntry(entry.startBooleanToggle(entry(slug), v.get())
                .setDefaultValue(v.getDefault())
                .setSaveConsumer(v::set)
                .build());
    }

    private static void bool_(List<AbstractConfigListEntry> entries, ConfigEntryBuilder entry, String slug,
                              ForgeConfigSpec.BooleanValue v) {
        entries.add(entry.startBooleanToggle(entry(slug), v.get())
                .setDefaultValue(v.getDefault())
                .setSaveConsumer(v::set)
                .build());
    }

    private static void dbl_(ConfigCategory cat, ConfigEntryBuilder entry, String slug, ForgeConfigSpec.DoubleValue v) {
        cat.addEntry(entry.startDoubleField(entry(slug), v.get())
                .setDefaultValue(v.getDefault())
                .setSaveConsumer(n -> {
                    try {
                        v.set(n);
                    } catch (IllegalArgumentException ignored) {
                    }
                })
                .build());
    }

    private static void dbl_(List<AbstractConfigListEntry> entries, ConfigEntryBuilder entry, String slug,
                             ForgeConfigSpec.DoubleValue v) {
        entries.add(entry.startDoubleField(entry(slug), v.get())
                .setDefaultValue(v.getDefault())
                .setSaveConsumer(n -> {
                    try {
                        v.set(n);
                    } catch (IllegalArgumentException ignored) {
                    }
                })
                .build());
    }

    private static void int_(ConfigCategory cat, ConfigEntryBuilder entry, String slug, ForgeConfigSpec.IntValue v) {
        cat.addEntry(entry.startIntField(entry(slug), v.get())
                .setDefaultValue(v.getDefault())
                .setSaveConsumer(n -> {
                    try {
                        v.set(n);
                    } catch (IllegalArgumentException ignored) {
                    }
                })
                .build());
    }

    private static void int_(List<AbstractConfigListEntry> entries, ConfigEntryBuilder entry, String slug,
                             ForgeConfigSpec.IntValue v) {
        entries.add(entry.startIntField(entry(slug), v.get())
                .setDefaultValue(v.getDefault())
                .setSaveConsumer(n -> {
                    try {
                        v.set(n);
                    } catch (IllegalArgumentException ignored) {
                    }
                })
                .build());
    }

    private static void lst_(ConfigCategory cat, ConfigEntryBuilder entry, String slug,
                             ForgeConfigSpec.ConfigValue<List<? extends String>> v) {
        cat.addEntry(entry.startStrList(entry(slug), new ArrayList<>(v.get()))
                .setDefaultValue(new ArrayList<>(v.getDefault()))
                .setSaveConsumer(v::set)
                .build());
    }

    private static void lst_(List<AbstractConfigListEntry> entries, ConfigEntryBuilder entry, String slug,
                             ForgeConfigSpec.ConfigValue<List<? extends String>> v) {
        entries.add(entry.startStrList(entry(slug), new ArrayList<>(v.get()))
                .setDefaultValue(new ArrayList<>(v.getDefault()))
                .setSaveConsumer(v::set)
                .build());
    }
}