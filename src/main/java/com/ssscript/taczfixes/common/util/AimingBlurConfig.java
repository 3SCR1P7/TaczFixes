package com.ssscript.taczfixes.common.util;

/**
 * 配件 display JSON 的 aiming_blur 字段(仅对瞄具生效), 控制使用该瞄具时的开镜模糊参数。
 * 字段为 null 表示未配置, 逐项回退到配置文件。
 */
public class AimingBlurConfig {
    public Boolean aim_blur_enabled;
    public Float aim_blur_range;
    public Float aim_blur_strength;
    public Float aim_blur_far_distance;
    public Float aim_blur_min_factor;
}
