package com.ssscript.taczfixes.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端常凪冻结状态:
 * <ul>
 *   <li>本机玩家被冻结: 服务端同步剩余 tick, 本地按毫秒倒计时;</li>
 *   <li>任意实体被冻结: 按实体 id 记录到期时间, 用于暂停该实体的 tick(动画/声音/状态)。</li>
 * </ul>
 */
public final class FreezeClientState {
    private static volatile long frozenUntilMillis;
    private static final Map<Integer, Long> FROZEN_ENTITIES = new ConcurrentHashMap<>();

    private FreezeClientState() {
    }

    /** 本机玩家: 剩余 tick; <=0 表示解除冻结。多次同步取"更久"的那个。 */
    public static void set(int ticks) {
        if (ticks <= 0) {
            frozenUntilMillis = 0L;
            return;
        }
        long until = System.currentTimeMillis() + ticks * 50L;
        if (until > frozenUntilMillis) {
            frozenUntilMillis = until;
        }
    }

    public static boolean isFrozen() {
        return System.currentTimeMillis() < frozenUntilMillis;
    }

    /** 实体冻结同步: 剩余 tick; <=0 表示解除。 */
    public static void setEntity(int entityId, int ticks) {
        if (ticks <= 0) {
            FROZEN_ENTITIES.remove(entityId);
            return;
        }
        long until = System.currentTimeMillis() + ticks * 50L;
        FROZEN_ENTITIES.merge(entityId, until, Math::max);
    }

    public static boolean isEntityFrozen(int entityId) {
        Long until = FROZEN_ENTITIES.get(entityId);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            FROZEN_ENTITIES.remove(entityId, until);
            return false;
        }
        return true;
    }

    public static boolean isEntityFrozen(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity == Minecraft.getInstance().player) {
            return isFrozen();
        }
        return isEntityFrozen(entity.getId());
    }

    /** 清理过期条目, 避免实体卸载后残留。 */
    public static void pruneExpired() {
        long now = System.currentTimeMillis();
        FROZEN_ENTITIES.entrySet().removeIf(entry -> now >= entry.getValue());
        if (now >= frozenUntilMillis) {
            frozenUntilMillis = 0L;
        }
    }

    /** 冻结实体的渲染姿态: 固定位置/朝向插值基准, 清空行走/攻击/受伤/死亡等动画状态, 避免抽搐。 */
    public static void freezePose(net.minecraft.world.entity.LivingEntity entity) {
        entity.setOldPosAndRot();
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        entity.yBodyRot = entity.getYRot();
        entity.yBodyRotO = entity.getYRot();
        entity.walkAnimation.setSpeed(0.0F);
        entity.attackAnim = 0.0F;
        entity.oAttackAnim = 0.0F;
        entity.swinging = false;
        entity.swingTime = 0;
        entity.hurtTime = 0;
        entity.deathTime = 0;
    }
    public static void clear() {
        frozenUntilMillis = 0L;
        FROZEN_ENTITIES.clear();
    }
}
