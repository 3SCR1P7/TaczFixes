package com.ssscript.taczfixes.common.util;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 换弹附加装填(bullet_in_barrel)的每"换弹上下文"状态跟踪:
 * 换弹开始时记录换弹前弹药量 x, 装填阶段完成时消费 min(x, n)。
 *
 * <p>key 用 {@code ShooterDataHolder} 实例而不是玩家 UUID: 双持时主手/副手各自持有独立的
 * data holder, 同时换弹不会互相覆盖 preAmmo / APPLIED。</p>
 */
public final class ReloadExtraTracker {
    private static final Map<Object, Integer> PRE_AMMO = new ConcurrentHashMap<>();
    private static final Set<Object> APPLIED = ConcurrentHashMap.newKeySet();
    /** 玩家 -> 其用过的上下文 key, 退出登录时按玩家兜底清理, 避免泄漏。 */
    private static final Map<UUID, Set<Object>> PLAYER_KEYS = new ConcurrentHashMap<>();

    private ReloadExtraTracker() {
    }

    /** 换弹开始: 记录换弹前的弹药量。 */
    public static void capture(UUID player, Object key, int preAmmo) {
        if (key == null) {
            return;
        }
        PRE_AMMO.put(key, preAmmo);
        APPLIED.remove(key);
        if (player != null) {
            PLAYER_KEYS.computeIfAbsent(player, id -> ConcurrentHashMap.newKeySet()).add(key);
        }
    }

    /** 取出换弹前弹药量(未记录时返回 0)。 */
    public static int getPreAmmo(Object key) {
        Integer value = key == null ? null : PRE_AMMO.get(key);
        return value == null ? 0 : value;
    }

    /** 本次换弹是否已应用过附加装填。 */
    public static boolean isApplied(Object key) {
        return key != null && APPLIED.contains(key);
    }

    public static void markApplied(Object key) {
        if (key != null) {
            APPLIED.add(key);
        }
    }

    /** 玩家退出时清理换弹附加装填状态。 */
    public static void clear(UUID player) {
        if (player == null) {
            return;
        }
        Set<Object> keys = PLAYER_KEYS.remove(player);
        if (keys != null) {
            for (Object key : keys) {
                PRE_AMMO.remove(key);
                APPLIED.remove(key);
            }
        }
    }
}
