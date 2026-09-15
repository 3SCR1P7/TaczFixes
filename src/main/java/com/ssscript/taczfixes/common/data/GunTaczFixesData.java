package com.ssscript.taczfixes.common.data;

import java.util.Map;

public class GunTaczFixesData {
    public Integer refit_point;
    public Double limb_factor;
    public Boolean allow_animation_zoom;
    /** 自定义 HUD id(assets/<ns>/hud/<path>.json); 填写后 tacz 原生 HUD 隐藏。 */
    public String custom_hud;
    public Map<String, InaccuracyConfig> inaccuracy_multiplier;
    public Map<String, RecoilConfig> recoil_multiplier;
    @com.google.gson.annotations.JsonAdapter(AttachmentSlotsConfig.Adapter.class)
    public AttachmentSlotsConfig attachment_slots;
    public FireKnockbackConfig fire_knockback;
    public AimingStaminaConfig aiming_stamina;
    public StaminaConfig stamina;
    public Integer bullet_in_barrel;
    public JumpInaccuracyConfig jump_inaccuracy;
    public ShieldConfig shield;
    public DamageByDistanceConfig damage_by_distance;
    public Map<String, CustomFireModeConfig> fire_mode;
    public Map<String, Object> fire_mode_adjust;
    public RicochetConfig bullet_ricochet;
    public DualWieldConfig dual_wield;

    /** 枪械 data 中的上肢耐力覆盖配置; 未填写的字段使用配置文件中的值。 */
    public static class AimingStaminaConfig {
        public Double consumption_multiplier;
        public Double recovery_multiplier;
        public Integer recovery_delay;
        public Double min_stamina_to_aim;
        public Double hold_breath_consumption_multiplier;
        public Double weight_consumption;
        public Double sway_amplitude;
        public Double sway_speed;
        public Double sway_low_stamina_multiplier;
        public Double sway_hold_breath_low_multiplier;
        public Double sway_sneak_multiplier;
        public Double sway_crawl_multiplier;
        public Double melee_cost;
        public Double shoot_cost;
        public Integer sway_hold_breath_calm;
    }

    /** 枪械 data 中的耐力倍率配置; 未填写的字段使用 1.0。 */
    public static class StaminaConfig {
        public Double consumption_multiplier;
        public Double recovery_multiplier;
    }

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

    public static class JumpInaccuracyConfig {
        public Double multiplier;
        public Double speed;
    }

    /** 枪盾 taczfixes 配置。resistance 未填默认 1.0；cooldown 未填默认 5s；durability 未填不进入冷却。 */
    public static class ShieldConfig {        public Double resistance;
        public Integer durability;
        public Double cooldown;
    }

    /** 距离-伤害倍率函数。y=倍率, x=飞行距离-effective_range；function 为 lua 表达式。 */
    public static class DamageByDistanceConfig {
        public Double effective_range;
        public String function;
    }

    /** 枪械 data 子弹跳弹配置。各字段与配置文件 bullet_ricochet 部分一致。
     * 未填写的字段回退到配置文件值；字段存在时覆盖配置文件(含 enable)。 */
    public static class RicochetConfig {
        public Boolean enable;
        public Double min_angle;
        public Double max_angle;
        public Double chance_min;
        public Double chance_max;
        public java.util.List<String> block_tags;
        public Double damage_multiplier;
        public Double reflect_angle_ratio_min;
        public Double reflect_angle_ratio_max;
        public Boolean top_bottom_enable;
    }

    /** 枪械 data 中 taczfixes.dual_wield: 逐枪覆盖双持模组配置, 优先级高于其 config 文件。 */
    public static class DualWieldConfig {
        public Boolean enable;
        public Double recoil_multiplier;
        public Double left_offset;
        public Double right_offset;
    }

    /** 自定义开火模式。</summary> */
    public static class CustomFireModeConfig {
        public String type;
        public BurstConfig burst_data;
        public Map<String, Double> adjust;
        public String icon;

        public static class BurstConfig {
            /** 兼容写法: fire_mode.<id>.burst_data.burst_data 直接使用 TACZ 原生结构, 优先级最高。 */
            public com.tacz.guns.resource.pojo.data.gun.BurstData burst_data;
            /** 平铺写法: fire_mode.<id>.burst_data 下的字段。 */
            public Boolean continuous_shoot;
            public Integer count;
            public Integer bpm;
            public Double min_interval;

            /** 解析出实际生效的 BurstData: 优先嵌套的原生结构, 否则由平铺字段构造。 */
            public com.tacz.guns.resource.pojo.data.gun.BurstData resolve(com.tacz.guns.resource.pojo.data.gun.BurstData fallback) {
                if (burst_data != null) {
                    return burst_data;
                }
                return fallback;
            }
        }

        public static Map<String, Double> toAdjustMap(Map<String, Object> raw) {
            if (raw == null) return null;
            java.util.Map<String, Double> out = new java.util.HashMap<>();
            raw.forEach((k, v) -> {
                if (v instanceof Number num) {
                    out.put(k, num.doubleValue());
                }
            });
            return out;
        }
    }
}
