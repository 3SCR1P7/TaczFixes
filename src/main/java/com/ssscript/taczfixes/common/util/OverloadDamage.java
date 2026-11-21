package com.ssscript.taczfixes.common.util;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OverloadDamage {
    private static final Map<UUID, Float> FACTORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SET_AT = new ConcurrentHashMap<>();

    /**
     * 超载倍率只在"刚消耗完超载弹"的一小段时间内有效: 同一发/同一 burst 的多颗弹丸都在这段时间内生成,
     * 而不经过 reduceAmmoOnce 的发射路径(或切枪/等待)拿不到旧倍率。
     */
    private static final long MAX_AGE_MS = 250L;

    private OverloadDamage() {
    }

    public static void setFactor(LivingEntity shooter, float factor) {
        UUID key = shooter.getUUID();
        if (factor <= 1.0F) {
            FACTORS.remove(key);
            SET_AT.remove(key);
        } else {
            FACTORS.put(key, factor);
            SET_AT.put(key, System.currentTimeMillis());
        }
    }

    public static float getFactor(LivingEntity shooter) {
        if (shooter == null) {
            return 1.0F;
        }
        UUID key = shooter.getUUID();
        Float factor = FACTORS.get(key);
        if (factor == null) {
            return 1.0F;
        }
        Long setAt = SET_AT.get(key);
        if (setAt == null || System.currentTimeMillis() - setAt > MAX_AGE_MS) {
            FACTORS.remove(key);
            SET_AT.remove(key);
            return 1.0F;
        }
        return factor;
    }

    /** 玩家退出登录时清理: 否则超载倍率会跨会话残留, 被非 reduceAmmoOnce 的发射路径白嫖。 */
    public static void clear(java.util.UUID player) {
        if (player != null) {
            FACTORS.remove(player);
            SET_AT.remove(player);
        }
    }
}
