package com.ssscript.taczfixes.common.data;

import java.util.Map;

public class GunTaczFixesData {
    public Integer refit_point;
    public Double limb_factor;
    public Boolean allow_animation_zoom;
    public Map<String, InaccuracyConfig> inaccuracy_multiplier;
    public Map<String, RecoilConfig> recoil_multiplier;
    public Map<String, CustomSlotDefinition> attachment_slots;
    public FireKnockbackConfig fire_knockback;
    public Map<String, String> ammo_replace;
    public Integer bullet_in_barrel;

    public static class InaccuracyConfig {
        public Integer cooldown_delay;
        public Double cooldown_speed;
        public Integer max_stack;
        public Double shot_percent;
        public Double shot_addend;
    }

    public static class RecoilConfig {
        public Double pitch_multiplier;
        public Double yaw_multiplier;
        public Integer window;
        public Map<String, RecoilModifierConfig> modifiers;
    }

    public static class RecoilModifierConfig {
        public Integer count;
        public Integer count_start;
        public Integer count_end;
        public Integer count_step;
        public Double pitch_multiplier;
        public Double yaw_multiplier;
    }

    public static class FireKnockbackConfig {
        public Double power;
        public Double power_sneak;
        public Double multiplier_semi;
        public Double multiplier_burst;
    }
}
