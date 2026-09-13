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
    public AimingStaminaAdjust aiming_stamina;
    public StaminaAdjust stamina;
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

    /** 对上肢耐力各数值的修饰符(在枪械 data / 配置文件的值之上生效)。 */
    public static class AimingStaminaAdjust {
        public Modifier consumption_multiplier;
        public Modifier recovery_multiplier;
        public Modifier recovery_delay;
        public Modifier min_stamina_to_aim;
        public Modifier hold_breath_consumption_multiplier;
        public Modifier weight_consumption;
        public Modifier sway_amplitude;
        public Modifier sway_speed;
        public Modifier sway_low_stamina_multiplier;
        public Modifier sway_hold_breath_low_multiplier;
        public Modifier sway_sneak_multiplier;
        public Modifier sway_crawl_multiplier;
        public Modifier melee_cost;
        public Modifier shoot_cost;
        public Modifier sway_hold_breath_calm;
    }

    /** 对耐力消耗/恢复倍率的修饰符。 */
    public static class StaminaAdjust {
        public Modifier consumption_multiplier;
        public Modifier recovery_multiplier;
    }
}
