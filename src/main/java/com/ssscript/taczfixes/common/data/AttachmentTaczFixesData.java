package com.ssscript.taczfixes.common.data;

import com.tacz.guns.resource.pojo.data.attachment.Modifier;

import java.util.List;
import java.util.Map;

public class AttachmentTaczFixesData {
    public Integer refit_point_consume;
    public Modifier limb_factor;
    public Map<String, GunTaczFixesData.RecoilConfig> recoil_multiplier;
    public Modifier fire_knockback_power;
    public Modifier friction;
    public Modifier gravity;
    public Modifier bullet_life;
    public Modifier manual_action_time;
    public Modifier reload_time;
    public Modifier sprint_time;
    public Modifier ammo_amount;
    public List<String> fire_mode_enable;
    public List<String> fire_mode_disable;
    public InaccuracyAdjust inaccuracy_multiplier;
    public JumpInaccuracyAdjust jump_inaccuracy;
    public GunTaczFixesData.ShieldConfig shield;

    public static class InaccuracyAdjust {
        public Modifier max_stack;
        public Modifier per_shot;
        public Modifier cooldown_speed;
        public Modifier cooldown_delay;
    }

    public static class JumpInaccuracyAdjust {
        public Modifier multiplier;
        public Modifier speed;
    }
}
