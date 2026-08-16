package com.ssscript.taczfixes.data;

import java.util.Map;

public class GunTaczFixesData {
    public Double limb_factor;
    public Map<String, InaccuracyConfig> inaccuracy_multiplier;
    public Map<String, RecoilConfig> recoil_multiplier;
    public Map<String, CustomSlotDefinition> attachment_slots;

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
        public Double pitch_multiplier;
        public Double yaw_multiplier;
    }
}
