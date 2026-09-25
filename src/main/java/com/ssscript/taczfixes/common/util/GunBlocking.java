package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** blocking 字段: 前方 distance 格内有方块/实体时, 按距离线性产生旋转与后退。 */
public final class GunBlocking {
    private static final double DEFAULT_DISTANCE_MAX = 0.5d;
    private static final double DEFAULT_DISTANCE_MIN = 0.25d;
    private static final double DEFAULT_ANGLE = 60.0d;
    private static final double DEFAULT_BACK_OFF = 0.25d;
    private static final double DEFAULT_DEFLECTION = 1.0d;
    private static final double DEFAULT_DISABLE_FIRE = 0.0d;
    private static final double DEFAULT_FACING = 180.0d;
    private static final double DEFAULT_FACING_DUAL = 90.0d;

    private GunBlocking() {
    }

    @Nullable
    public static GunTaczFixesData.BlockingConfig resolve(ItemStack gunStack) {
        return TaczFixesDataManager.resolveBlocking(gunStack);
    }

    /** 是否启用阻挡: 枪械 data 的 enable 优先, 否则使用全局配置(默认 false)。 */
    public static boolean isEnabled(ItemStack gunStack) {
        GunTaczFixesData.BlockingConfig cfg = resolve(gunStack);
        if (cfg != null && cfg.enable != null) {
            return cfg.enable;
        }
        return com.ssscript.taczfixes.common.config.Config.BLOCKING_ENABLE.get();
    }

    public static double distanceMax(@Nullable GunTaczFixesData.BlockingConfig cfg) {
        double value = cfg == null || cfg.distance_max == null
                ? com.ssscript.taczfixes.common.config.Config.BLOCKING_DISTANCE_MAX.get() : cfg.distance_max;
        return Math.max(0.01d, value);
    }

    public static double distanceMin(@Nullable GunTaczFixesData.BlockingConfig cfg) {
        double value = cfg == null || cfg.distance_min == null
                ? com.ssscript.taczfixes.common.config.Config.BLOCKING_DISTANCE_MIN.get() : cfg.distance_min;
        return Math.max(0.0d, Math.min(value, distanceMax(cfg)));
    }

    public static double angleDeg(@Nullable GunTaczFixesData.BlockingConfig cfg) {
        return cfg == null || cfg.angle == null
                ? com.ssscript.taczfixes.common.config.Config.BLOCKING_ANGLE.get() : cfg.angle;
    }

    public static double backOff(@Nullable GunTaczFixesData.BlockingConfig cfg) {
        return cfg == null || cfg.back_off == null
                ? com.ssscript.taczfixes.common.config.Config.BLOCKING_BACK_OFF.get() : cfg.back_off;
    }

    public static double deflection(@Nullable GunTaczFixesData.BlockingConfig cfg) {
        double value = cfg == null || cfg.deflection == null
                ? com.ssscript.taczfixes.common.config.Config.BLOCKING_DEFLECTION.get() : cfg.deflection;
        return Math.max(0.0d, value);
    }

    public static double disableFire(@Nullable GunTaczFixesData.BlockingConfig cfg) {
        double value = cfg == null || cfg.disable_fire == null
                ? com.ssscript.taczfixes.common.config.Config.BLOCKING_DISABLE_FIRE.get() : cfg.disable_fire;
        return Math.max(0.0d, value);
    }

    public static double facing(@Nullable GunTaczFixesData.BlockingConfig cfg) {
        return cfg == null || cfg.facing == null
                ? com.ssscript.taczfixes.common.config.Config.BLOCKING_FACING.get() : cfg.facing;
    }

    public static double facingDual(@Nullable GunTaczFixesData.BlockingConfig cfg) {
        return cfg == null || cfg.facing_dual_wield == null
                ? com.ssscript.taczfixes.common.config.Config.BLOCKING_FACING_DUAL_WIELD.get() : cfg.facing_dual_wield;
    }

    /** 到最近障碍距离小于 disable_fire 时禁止开火。 */
    public static boolean isFireDisabled(@Nullable Player player, ItemStack gunStack) {
        if (player == null || gunStack == null || gunStack.isEmpty() || !isEnabled(gunStack)) {
            return false;
        }
        GunTaczFixesData.BlockingConfig cfg = resolve(gunStack);
        double threshold = disableFire(cfg);
        if (threshold <= 0.0d) {
            return false;
        }
        double max = Math.max(distanceMax(cfg), threshold);
        return nearestDistance(player, max) < threshold;
    }

    /** 阻挡强度 0..1: 距离 >= distance_max 为 0, 距离 <= distance_min 为 1, 中间线性。 */
    public static double factor(@Nullable Player player, double distanceMax, double distanceMin) {
        double nearest = nearestDistance(player, distanceMax);
        double span = distanceMax - distanceMin;
        if (span <= 1.0E-6d) {
            return nearest <= distanceMin ? 1.0d : 0.0d;
        }
        double factor = (distanceMax - nearest) / span;
        return Math.max(0.0d, Math.min(1.0d, factor));
    }

    /** 前方最近障碍距离(距玩家眼位, 格); 无阻挡返回 distanceMax。 */
    public static double nearestDistance(@Nullable Player player, double distanceMax) {
        return distanceMax * nearestRatio(player, distanceMax);
    }

    private static double nearestRatio(@Nullable Player player, double distance) {
        if (player == null || distance <= 0.0d) {
            return 1.0d;
        }
        Level level = player.level();
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 to = from.add(look.scale(distance));
        double nearest = distance;
        BlockHitResult blockHit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            nearest = Math.min(nearest, from.distanceTo(blockHit.getLocation()));
        }
        AABB box = new AABB(from, to).inflate(0.3d);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, from, to, box,
                entity -> entity != player && entity.isPickable() && !entity.isSpectator(),
                distance * distance);
        if (entityHit != null) {
            nearest = Math.min(nearest, from.distanceTo(entityHit.getLocation()));
        }
        double ratio = nearest / distance;
        return Math.max(0.0d, Math.min(1.0d, ratio));
    }

    /** 子弹发射位置偏移: 沿 facing 方向(0=右,90=上,180=左,-90=下), 大小为 距离 * deflection * tan(角度)。 */
    public static Vec3 shotOffset(Vec3 look, double obstacleDistance, double angleDeg, double deflection,
                                  double facingDeg) {
        double shift = Math.max(0.0d, obstacleDistance) * Math.max(0.0d, deflection)
                * Math.tan(Math.toRadians(angleDeg));
        if (shift <= 0.0d || look == null) {
            return Vec3.ZERO;
        }
        double radians = Math.toRadians(facingDeg);
        Vec3 direction = right(look).scale(Math.cos(radians))
                .add(new Vec3(0.0d, 1.0d, 0.0d).scale(Math.sin(radians)));
        if (direction.lengthSqr() < 1.0E-8d) {
            return Vec3.ZERO;
        }
        return direction.normalize().scale(shift);
    }

    /** 玩家左方向向量(水平)。 */
    public static Vec3 left(Vec3 look) {
        Vec3 left = new Vec3(0.0d, 1.0d, 0.0d).cross(look);
        if (left.lengthSqr() < 1.0E-8d) {
            return new Vec3(-1.0d, 0.0d, 0.0d);
        }
        return left.normalize();
    }

    /** 玩家右方向向量(水平)。 */
    public static Vec3 right(Vec3 look) {
        Vec3 right = look.cross(new Vec3(0.0d, 1.0d, 0.0d));
        if (right.lengthSqr() < 1.0E-8d) {
            return new Vec3(1.0d, 0.0d, 0.0d);
        }
        return right.normalize();
    }

    /** 绕任意轴旋转(Rodrigues)。 */
    public static Vec3 rotateAroundAxis(Vec3 vector, Vec3 axis, double radians) {
        Vec3 k = axis.normalize();
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return vector.scale(cos)
                .add(k.cross(vector).scale(sin))
                .add(k.scale(k.dot(vector) * (1.0d - cos)));
    }
}
