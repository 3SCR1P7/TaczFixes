package com.ssscript.taczfixes.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 换弹附加装填(bullet_in_barrel)的每玩家状态跟踪:
 * 换弹开始时记录换弹前弹药量 x, 装填阶段完成时消费 min(x, n)。
 */
public final class ReloadExtraTracker {
    private static final Map<UUID, Integer> PRE_AMMO = new ConcurrentHashMap<>();
    private static final Set<UUID> APPLIED = ConcurrentHashMap.newKeySet();

    private ReloadExtraTracker() {
    }

    /** 换弹开始: 记录换弹前的弹药量。 */
    public static void capture(UUID uuid, int preAmmo) {
        PRE_AMMO.put(uuid, preAmmo);
        APPLIED.remove(uuid);
    }

    /** 取出换弹前弹药量(未记录时返回 0)。 */
    public static int getPreAmmo(UUID uuid) {
        Integer value = PRE_AMMO.get(uuid);
        return value == null ? 0 : value;
    }

    /** 本次换弹是否已应用过附加装填。 */
    public static boolean isApplied(UUID uuid) {
        return APPLIED.contains(uuid);
    }

    public static void markApplied(UUID uuid) {
        APPLIED.add(uuid);
    }
}
