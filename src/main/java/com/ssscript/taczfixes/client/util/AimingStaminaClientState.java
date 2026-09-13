package com.ssscript.taczfixes.client.util;

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
        return isKnown() && cost > 0f && stamina < cost;
    }

    public static void reset() {
        stamina = -1f;
        max = -1f;
    }
}
