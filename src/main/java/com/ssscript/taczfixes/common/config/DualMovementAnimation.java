package com.ssscript.taczfixes.common.config;

/** 双持移动动画预设。默认1为当前动画, 默认2为 left_movement_2 / right_movement_2。 */
public enum DualMovementAnimation {
    DEFAULT_1,
    DEFAULT_2;

    public String translationKey() {
        return "config.taczfixes.value.dual_wield.movement_animation." + name().toLowerCase(java.util.Locale.ROOT);
    }
}