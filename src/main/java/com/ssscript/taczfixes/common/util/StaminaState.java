package com.ssscript.taczfixes.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 服务端玩家的耐力状态。 */
public final class StaminaState {

    private static final Map<UUID, Float> STAMINA = new HashMap<>();
    private static final Map<UUID, Long> LAST_CONSUME_TICK = new HashMap<>();
    private static final Map<UUID, Float> LAST_SYNCED = new HashMap<>();
    private static final Map<UUID, Double> LAST_X = new HashMap<>();
    private static final Map<UUID, Double> LAST_Z = new HashMap<>();
    private static final java.util.Set<UUID> CLIENT_SPRINTING = new java.util.HashSet<>();
    private static final java.util.Set<UUID> EXHAUSTED = new java.util.HashSet<>();

    private StaminaState() {
    }

    public static float getStamina(UUID uuid, float max) {
        Float value = STAMINA.get(uuid);
        return value == null ? max : value;
    }

    public static void setStamina(UUID uuid, float value) {
        STAMINA.put(uuid, value);
    }

    public static long getLastConsumeTick(UUID uuid) {
        Long value = LAST_CONSUME_TICK.get(uuid);
        return value == null ? -1L : value;
    }

    public static void setLastConsumeTick(UUID uuid, long tick) {
        LAST_CONSUME_TICK.put(uuid, tick);
    }

    public static float getLastSynced(UUID uuid) {
        Float value = LAST_SYNCED.get(uuid);
        return value == null ? Float.NaN : value;
    }

    public static void setLastSynced(UUID uuid, float value) {
        LAST_SYNCED.put(uuid, value);
    }

    /** 更新并返回与上一 tick 位置的水平距离平方(用于判断是否在行走)。 */
    public static double movedDistanceSqr(UUID uuid, double x, double z) {
        Double lastX = LAST_X.get(uuid);
        Double lastZ = LAST_Z.get(uuid);
        LAST_X.put(uuid, x);
        LAST_Z.put(uuid, z);
        if (lastX == null || lastZ == null) return 0.0;
        double dx = x - lastX;
        double dz = z - lastZ;
        return dx * dx + dz * dz;
    }

    /** 客户端上报的疾跑状态(用于消耗判定)。 */
    public static boolean isClientSprinting(UUID uuid) {
        return CLIENT_SPRINTING.contains(uuid);
    }

    public static void setClientSprinting(UUID uuid, boolean value) {
        if (value) {
            CLIENT_SPRINTING.add(uuid);
        } else {
            CLIENT_SPRINTING.remove(uuid);
        }
    }

    /** 是否处于力竭状态(耐力归零进入, 恢复到阈值后结束)。 */
    public static boolean isExhausted(UUID uuid) {
        return EXHAUSTED.contains(uuid);
    }

    public static void setExhausted(UUID uuid, boolean value) {
        if (value) {
            EXHAUSTED.add(uuid);
        } else {
            EXHAUSTED.remove(uuid);
        }
    }

    public static void remove(UUID uuid) {
        STAMINA.remove(uuid);
        LAST_CONSUME_TICK.remove(uuid);
        LAST_SYNCED.remove(uuid);
        LAST_X.remove(uuid);
        LAST_Z.remove(uuid);
        CLIENT_SPRINTING.remove(uuid);
        EXHAUSTED.remove(uuid);
    }
}
