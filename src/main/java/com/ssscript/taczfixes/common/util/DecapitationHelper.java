package com.ssscript.taczfixes.common.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DecapitationHelper {
    private static final Map<UUID, Byte> BULLET_STATE = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> SHOOTER_BONUS = new ConcurrentHashMap<>();

    private DecapitationHelper() {
    }

    public static void onBulletHitEntity(Entity bullet, Entity shooter, boolean headshot, float bonusPerHeadshot) {
        if (bullet == null || shooter == null) {
            return;
        }
        UUID bulletId = bullet.getUUID();
        byte state = BULLET_STATE.getOrDefault(bulletId, (byte) 0);
        if (headshot) {
            if (state != 2) {
                BULLET_STATE.put(bulletId, (byte) 1);
                if (bonusPerHeadshot > 0.0F) {
                    SHOOTER_BONUS.merge(shooter.getUUID(), bonusPerHeadshot, Float::sum);
                }
            }
        } else if (state == 0) {
            BULLET_STATE.put(bulletId, (byte) 2);
            SHOOTER_BONUS.remove(shooter.getUUID());
        }
    }

    public static void onBulletRemoved(Entity bullet) {
        if (bullet == null || bullet.level().isClientSide) {
            return;
        }
        UUID bulletId = bullet.getUUID();
        byte state = BULLET_STATE.getOrDefault(bulletId, (byte) 0);
        if (state != 1) {
            Entity owner = bullet instanceof Projectile projectile ? projectile.getOwner() : null;
            if (owner != null) {
                SHOOTER_BONUS.remove(owner.getUUID());
            }
        }
        BULLET_STATE.remove(bulletId);
    }

    public static float getBonus(UUID shooterId) {
        return SHOOTER_BONUS.getOrDefault(shooterId, 0.0F);
    }
}
