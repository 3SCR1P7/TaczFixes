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

    /**
     * 阻挡时模型水平移动格数(负数向左, 正数向右)。
     * 枪械 data 配置优先, 未配置时使用全局配置。
     */
    public static double offsetYaw(@Nullable GunTaczFixesData.BlockingConfig cfg, ItemStack gunStack) {
        if (cfg != null && cfg.offset_yaw != null) {
            return cfg.offset_yaw;
        }
        return com.ssscript.taczfixes.common.config.Config.BLOCKING_OFFSET_YAW.get();
    }

    private static boolean isPistol(ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            return false;
        }
        return com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gun.getGunId(gunStack))
                .map(index -> "pistol".equalsIgnoreCase(index.getType()))
                .orElse(false);
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

    public static double disableAiming(@Nullable GunTaczFixesData.BlockingConfig cfg) {
        double value = cfg == null || cfg.disable_aiming == null
                ? com.ssscript.taczfixes.common.config.Config.BLOCKING_DISABLE_AIMING.get() : cfg.disable_aiming;
        return Math.max(0.0d, value);
    }

    /** 到最近障碍距离小于 disable_aiming 时禁用开镜。 */
    public static boolean isAimingDisabled(@Nullable Player player, ItemStack gunStack) {
        if (player == null || gunStack == null || gunStack.isEmpty() || !isEnabled(gunStack)) {
            return false;
        }
        GunTaczFixesData.BlockingConfig cfg = resolve(gunStack);
        double threshold = disableAiming(cfg);
        if (threshold <= 0.0d) {
            return false;
        }
        double max = Math.max(distanceMax(cfg), threshold);
        return nearestDistance(player, max) < threshold;
    }

    /** 非双持时的偏转方向: 枪械 data 优先; 未配置时手枪使用全局手枪配置, 其它使用全局配置。 */
    public static double facing(@Nullable GunTaczFixesData.BlockingConfig cfg, ItemStack gunStack) {
        if (cfg != null && cfg.facing != null) {
            return cfg.facing;
        }
        return isPistol(gunStack)
                ? com.ssscript.taczfixes.common.config.Config.BLOCKING_FACING_PISTOL.get()
                : com.ssscript.taczfixes.common.config.Config.BLOCKING_FACING.get();
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

    // ---- 模型与弹道共用的平滑因子 ----
    // 模型(客户端每帧更新)和弹道(服务端每 tick 更新)取同一个平滑值, 保证视觉偏转角与射击偏转角一致。
    private static final float SMOOTH_TAU = 0.12f;
    private static final java.util.Map<java.util.UUID, SmoothState> SMOOTH = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class SmoothState {
        float mainFactor;
        float offhandFactor;
        long mainNanos;
        long offhandNanos;
    }

    /** 服务端每 tick 刷新主/副手平滑因子(子弹用)。 */
    public static void tick(@Nullable Player player) {
        if (player == null) {
            return;
        }
        updateSmooth(player, false, targetFactor(player, player.getMainHandItem()));
        updateSmooth(player, true, targetFactor(player, player.getOffhandItem()));
    }

    /** 客户端每帧刷新指定手的平滑因子并返回(模型用)。 */
    public static float updateAndGetFactor(@Nullable Player player, boolean offhand) {
        if (player == null) {
            return 0.0f;
        }
        updateSmooth(player, offhand, targetFactor(player, offhand ? player.getOffhandItem() : player.getMainHandItem()));
        return smoothedFactor(player, offhand);
    }

    /** 当前平滑后的阻挡强度; 尚无状态时回退为瞬时值。 */
    public static float smoothedFactor(@Nullable Player player, boolean offhand) {
        if (player == null) {
            return 0.0f;
        }
        SmoothState state = SMOOTH.get(player.getUUID());
        if (state == null) {
            return targetFactor(player, offhand ? player.getOffhandItem() : player.getMainHandItem());
        }
        return offhand ? state.offhandFactor : state.mainFactor;
    }

    public static void clearState(@Nullable java.util.UUID playerId) {
        if (playerId != null) {
            SMOOTH.remove(playerId);
        }
    }

    private static float targetFactor(Player player, ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty() || IGun.getIGunOrNull(gunStack) == null
                || !isEnabled(gunStack)) {
            return 0.0f;
        }
        GunTaczFixesData.BlockingConfig cfg = resolve(gunStack);
        return (float) factor(player, distanceMax(cfg), distanceMin(cfg));
    }

    private static void updateSmooth(Player player, boolean offhand, float target) {
        SmoothState state = SMOOTH.computeIfAbsent(player.getUUID(), id -> new SmoothState());
        long now = System.nanoTime();
        float current = offhand ? state.offhandFactor : state.mainFactor;
        long last = offhand ? state.offhandNanos : state.mainNanos;
        float dt = last == 0L ? 1.0f / 20.0f : Math.min((now - last) / 1_000_000_000.0f, 0.25f);
        float decay = (float) Math.exp(-dt / SMOOTH_TAU);
        float next = target + (current - target) * decay;
        if (Math.abs(target - next) < 0.001f) {
            next = target;
        }
        if (offhand) {
            state.offhandFactor = next;
            state.offhandNanos = now;
        } else {
            state.mainFactor = next;
            state.mainNanos = now;
        }
    }

    /** 枪械是否来自副手(双持)。 */
    public static boolean isOffhandGun(@Nullable Player player, ItemStack gunStack) {
        if (player == null || gunStack == null || gunStack.isEmpty()) {
            return false;
        }
        ItemStack offhand = player.getOffhandItem();
        return gunStack == offhand || DualWieldStackId.matches(gunStack, offhand);
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
        if (com.ssscript.taczfixes.common.config.Config.BLOCKING_ENTITY_ENABLE.get()) {
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, from, to, box,
                    entity -> entity != player && entity.isPickable() && !entity.isSpectator(),
                    distance * distance);
            if (entityHit != null) {
                nearest = Math.min(nearest, from.distanceTo(entityHit.getLocation()));
            }
        }
        double ratio = nearest / distance;
        return Math.max(0.0d, Math.min(1.0d, ratio));
    }

    /** 按 blocking 旋转射击角度(pitch/yaw), 返回 {pitch, yaw}; 未启用返回 null。 */
    @Nullable
    public static float[] rotateShotAngles(float pitch, float yaw, @Nullable Player player, ItemStack gunStack) {
        if (player == null || gunStack == null || gunStack.isEmpty() || !isEnabled(gunStack)) {
            return null;
        }
        GunTaczFixesData.BlockingConfig cfg = resolve(gunStack);
        // 与模型完全相同的平滑因子与角度, 保证视觉上弹道方向与枪身偏转一致。
        double factor = smoothedFactor(player, isOffhandGun(player, gunStack));
        if (factor <= 0.0d) {
            return null;
        }
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
        if (direction.lengthSqr() < 1.0E-8d) {
            return null;
        }
        boolean dual = DualWieldEligibility.isDualWielding(player);
        // 视觉补偿: 子弹绕眼位旋转, 模型绕模型枢轴做屏幕空间旋转, 等角时弹道观感偏小,
        // 该系数用于让弹道方向在视觉上与枪身偏转对齐(可配置, 默认 2.0 -> 1.5)。
        // 补偿系数上限同时作为过渡旋转角: angle=0 时为 max, angle>=max 时为 min。
        double multMax = com.ssscript.taczfixes.common.config.Config.BLOCKING_SHOT_MULTIPLIER_MAX.get();
        double multMin = com.ssscript.taczfixes.common.config.Config.BLOCKING_SHOT_MULTIPLIER_MIN.get();
        double multiplier;
        if (multMax <= 1.0E-6d) {
            multiplier = multMin;
        } else {
            double clamped = Math.max(0.0d, Math.min(angleDeg(cfg), multMax));
            multiplier = multMax - clamped / multMax * (multMax - multMin);
        }
        double angleRad = Math.toRadians(angleDeg(cfg) * factor);
        double facingRad = Math.toRadians(dual ? facingDual(cfg) : facing(cfg, gunStack));
        Vec3 axis = right(player.getLookAngle()).scale(Math.sin(facingRad))
                .subtract(new Vec3(0.0d, 1.0d, 0.0d).scale(Math.cos(facingRad)));
        if (axis.lengthSqr() < 1.0E-8d) {
            return null;
        }
        Vec3 rotated = rotateAroundAxis(direction, axis.normalize(), angleRad).normalize();
        // 补偿系数只作用于水平方向(yaw), 竖直方向(pitch)保持与模型相同的角度。
        float newPitch = (float) (-Math.asin(Math.max(-1.0d, Math.min(1.0d, rotated.y))) * 180.0d / Math.PI);
        float baseYaw = (float) (Math.atan2(-direction.x, direction.z) * 180.0d / Math.PI);
        float rotatedYaw = (float) (Math.atan2(-rotated.x, rotated.z) * 180.0d / Math.PI);
        float yawDelta = net.minecraft.util.Mth.wrapDegrees(rotatedYaw - baseYaw);
        float newYaw = net.minecraft.util.Mth.wrapDegrees(baseYaw + (float) (yawDelta * multiplier));
        return new float[]{newPitch, newYaw};
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
