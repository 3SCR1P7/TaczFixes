package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import com.tacz.guns.util.HitboxHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 玩家受击箱扩展:
 * - 游泳/爬行(趴姿): 随朝向的长条箱 长 1.8、宽 0.6、高 0.6, 前端为头部(爆头)、尾端为四肢;
 * - 站姿(配置开启 "更好的站立碰撞箱" 时): 尺寸与原版一致, 但随玩家朝向旋转;
 * - 站姿的爆头/四肢判定完全沿用原版 TaCZ 规则, 只改变判定箱的方向。
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

    /** 趴姿: 爬行或游泳(Pose.SWIMMING)。 */
    public static boolean isHorizontalPose(Entity entity) {
        return entity instanceof Player player && !player.isSpectator() && player.getPose() == Pose.SWIMMING;
    }

    /** 兼容旧调用: 爬行(非真正游泳), 诊断渲染等仍可用。 */
    public static boolean isCrawling(Entity entity) {
        return entity instanceof Player player && !player.isSpectator()
                && player.getPose() == Pose.SWIMMING && !player.isSwimming();
    }

    /** 趴姿受击箱是否启用。 */
    public static boolean isHorizontalHitboxActive(Entity entity) {
        return isHorizontalPose(entity) && Config.CRAWL_HITBOX_ENABLED.get();
    }

    /** 站姿旋转受击箱是否启用(非趴姿)。 */
    public static boolean isStandingHitboxActive(Entity entity) {
        if (!(entity instanceof Player player) || player.isSpectator()) {
            return false;
        }
        return player.getPose() != Pose.SWIMMING && Config.STANDING_HITBOX_ENABLED.get();
    }

    /** 该实体的子弹射线判定是否由本模组接管。 */
    public static boolean shouldUseCustomHitbox(Entity entity) {
        return isHorizontalHitboxActive(entity) || isStandingHitboxActive(entity);
    }

    /** 射线求交; 未接管时返回 null。 */
    public static Vec3 clip(Projectile projectile, Entity entity, Vec3 startVec, Vec3 endVec) {
        if (isHorizontalHitboxActive(entity)) {
            return clipHorizontal(entity, startVec, endVec);
        }
        if (isStandingHitboxActive(entity)) {
            AABB vanillaBox = HitboxHelper.getFixedBoundingBox(entity, projectile == null ? null : projectile.getOwner());
            return clipRotated(entity, vanillaBox, startVec, endVec);
        }
        return null;
    }

    private static Vec3 clipHorizontal(Entity entity, Vec3 startVec, Vec3 endVec) {
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
        return toWorld(hit.get(), center, right, forward);
    }

    /** 把原版 AABB 绕自身中心按玩家朝向旋转后再求交。 */
    private static Vec3 clipRotated(Entity entity, AABB vanillaBox, Vec3 startVec, Vec3 endVec) {
        Vec3 center = vanillaBox.getCenter();
        Vec3 forward = forward(entity);
        Vec3 right = new Vec3(forward.z, 0.0d, -forward.x);
        double halfX = vanillaBox.getXsize() / 2.0d;
        double halfY = vanillaBox.getYsize() / 2.0d;
        double halfZ = vanillaBox.getZsize() / 2.0d;
        Vec3 localStart = toLocal(startVec, center, right, forward);
        Vec3 localEnd = toLocal(endVec, center, right, forward);
        AABB box = new AABB(-halfX, -halfY, -halfZ, halfX, halfY, halfZ);
        Optional<Vec3> hit = box.clip(localStart, localEnd);
        if (hit.isEmpty()) {
            return null;
        }
        return toWorld(hit.get(), center, right, forward);
    }

    /** 原版 TaCZ 爆头判定: 命中点相对实体坐标落在头部判定盒(或眼高 ±0.25 区间)内。 */
    public static boolean isStandingHeadshot(Entity entity, Vec3 hitPos) {
        Vec3 relative = hitPos.subtract(entity.position());
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId != null) {
            AABB headBox = HeadShotAABBConfigRead.getAABB(typeId);
            if (headBox != null) {
                return headBox.contains(relative);
            }
        }
        double eyeHeight = entity.getEyeHeight();
        return relative.y > eyeHeight - 0.25d && relative.y < eyeHeight + 0.25d;
    }

    /** 趴姿长条箱的分区: 前端头部、尾端四肢、中间普通。 */
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

    private static Vec3 toWorld(Vec3 local, Vec3 center, Vec3 right, Vec3 forward) {
        return center.add(right.scale(local.x)).add(0.0d, local.y, 0.0d).add(forward.scale(local.z));
    }
}
