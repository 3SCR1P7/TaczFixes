package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.config.Config;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 爬行玩家的独立受击碰撞箱: 沿朝向长 1.8、宽 0.6、高 0.6。
 * 前端为头部(爆头), 尾端 X 长度为腿部(四肢), 中间为普通命中。
 */
public final class CrawlHitboxHelper {
    public static final double LENGTH = 1.8d;
    public static final double WIDTH = 0.6d;
    public static final double HEIGHT = 0.6d;

    public enum Zone {
        HEAD,
        BODY,
        LIMB
    }

    private CrawlHitboxHelper() {
    }

    public static boolean isCrawling(Entity entity) {
        if (!(entity instanceof Player player) || player.isSpectator()) {
            return false;
        }
        return player.getPose() == Pose.SWIMMING && !player.isSwimming();
    }

    /** 射线与爬行受击箱求交, 命中时返回世界坐标命中点。 */
    public static Vec3 clip(Entity entity, Vec3 startVec, Vec3 endVec) {
        if (!isCrawling(entity)) {
            return null;
        }
        Vec3 center = entity.position();
        Vec3 forward = forward(entity);
        Vec3 right = new Vec3(forward.z, 0.0d, -forward.x);
        Vec3 localStart = toLocal(startVec, center, right, forward);
        Vec3 localEnd = toLocal(endVec, center, right, forward);
        AABB box = new AABB(-WIDTH / 2.0d, 0.0d, -LENGTH / 2.0d, WIDTH / 2.0d, HEIGHT, LENGTH / 2.0d);
        Optional<Vec3> hit = box.clip(localStart, localEnd);
        if (hit.isEmpty()) {
            return null;
        }
        Vec3 local = hit.get();
        return center.add(right.scale(local.x)).add(0.0d, local.y, 0.0d).add(forward.scale(local.z));
    }

    public static Zone zoneOf(Entity entity, Vec3 hitPos) {
        double headLength = Config.CRAWL_HEADSHOT_LENGTH.get();
        double limbLength = Config.LIMB_THRESHOLD_STANDING.get();
        double along = hitPos.subtract(entity.position()).dot(forward(entity));
        if (along >= LENGTH / 2.0d - headLength) {
            return Zone.HEAD;
        }
        if (along <= -(LENGTH / 2.0d - limbLength)) {
            return Zone.LIMB;
        }
        return Zone.BODY;
    }

    private static Vec3 forward(Entity entity) {
        return Vec3.directionFromRotation(0.0f, entity.getYRot());
    }

    private static Vec3 toLocal(Vec3 position, Vec3 center, Vec3 right, Vec3 forward) {
        Vec3 offset = position.subtract(center);
        return new Vec3(offset.dot(right), offset.y, offset.dot(forward));
    }
}
