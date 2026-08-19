package com.ssscript.taczfixes.common.data;

import com.tacz.guns.resource.pojo.data.attachment.Modifier;

import java.util.Map;

public class AttachmentTaczFixesData {
    public Modifier limb_factor;
    public Map<String, GunTaczFixesData.RecoilConfig> recoil_multiplier;
    public Modifier fire_knockback_power;
    public InaccuracyAdjust inaccuracy_multiplier;

    public static class InaccuracyAdjust {
        public Modifier max_stack;
        public Modifier per_shot;
        public Modifier cooldown_speed;
        public Modifier cooldown_delay;
    }
}
