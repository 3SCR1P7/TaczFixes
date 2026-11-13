package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.config.Config;

/** 客户端缓存的当前上肢耐力(-1 表示尚未同步)。 */
public final class AimingStaminaClientState {

    private static float stamina = -1f;
    private static float max = -1f;

    private AimingStaminaClientState() {
    }

    public static void set(float stamina, float max) {
        AimingStaminaClientState.stamina = stamina;
        AimingStaminaClientState.max = max;
    }

    public static float getStamina() {
        return stamina;
    }

    public static float getMax() {
        return max;
    }

    public static boolean isKnown() {
        return stamina >= 0f;
    }

    public static boolean isInsufficient(float cost) {
        // 与服务端保持一致: 功能禁用时不拦截近战/开火
        if (!Config.AIMING_STAMINA_ENABLED.get()) return false;
        return isKnown() && cost > 0f && stamina < cost;
    }

    public static void reset() {
        stamina = -1f;
        max = -1f;
    }
}
