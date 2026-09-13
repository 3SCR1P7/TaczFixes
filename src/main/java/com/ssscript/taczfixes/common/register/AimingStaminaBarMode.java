package com.ssscript.taczfixes.common.register;

import java.util.Locale;

/** 上肢耐力条显示模式。 */
public enum AimingStaminaBarMode {
    /** 始终显示。 */
    ALWAYS,
    /** 从不显示。 */
    NEVER,
    /** 耐力不满时显示, 满后延迟一段时间逐渐隐藏。 */
    SMART;

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
