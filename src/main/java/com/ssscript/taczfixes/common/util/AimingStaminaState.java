package com.ssscript.taczfixes.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 服务端玩家的上肢耐力状态。 */
public final class AimingStaminaState {

    private static final Map<UUID, Float> STAMINA = new HashMap<>();
    private static final Map<UUID, Boolean> HOLD_BREATH = new HashMap<>();
    private static final Map<UUID, Long> LAST_AIM_TICK = new HashMap<>();
    private static final Map<UUID, Float> LAST_SYNCED = new HashMap<>();

    private AimingStaminaState() {
    }

    public static float getStamina(UUID uuid, float max) {
        Float value = STAMINA.get(uuid);
        return value == null ? max : value;
    }

    public static void setStamina(UUID uuid, float value) {
        STAMINA.put(uuid, value);
    }

    public static boolean isHoldingBreath(UUID uuid) {
        return Boolean.TRUE.equals(HOLD_BREATH.get(uuid));
    }

    public static void setHoldingBreath(UUID uuid, boolean value) {
        if (value) {
            HOLD_BREATH.put(uuid, Boolean.TRUE);
        } else {
            HOLD_BREATH.remove(uuid);
        }
    }

    public static long getLastAimTick(UUID uuid) {
        Long value = LAST_AIM_TICK.get(uuid);
        return value == null ? -1L : value;
    }

    public static void setLastAimTick(UUID uuid, long tick) {
        LAST_AIM_TICK.put(uuid, tick);
    }

    public static float getLastSynced(UUID uuid) {
        Float value = LAST_SYNCED.get(uuid);
        return value == null ? Float.NaN : value;
    }

    public static void setLastSynced(UUID uuid, float value) {
        LAST_SYNCED.put(uuid, value);
    }

    public static void remove(UUID uuid) {
        STAMINA.remove(uuid);
        HOLD_BREATH.remove(uuid);
        LAST_AIM_TICK.remove(uuid);
        LAST_SYNCED.remove(uuid);
    }
}
