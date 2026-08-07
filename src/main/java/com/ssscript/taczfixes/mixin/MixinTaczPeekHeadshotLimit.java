package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.PeekHeadshotHelper;
import com.tacz.guns.entity.EntityKineticBullet.EntityResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656peek.compat.tacz.TaczPeekHitboxHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GD656Peek 的 TaczPeekHitboxHelper.getHitResult 负责探头玩家的命中判定：
 * 命中下半身碰撞箱为非爆头，命中上半身碰撞箱即爆头。
 * 本 mixin 在其正常返回后复查命中点，若命中点不在上半身顶部
 * （顶部向下可配置高度，默认 0.4 格）之内，则把爆头降级为普通身体伤害。
 */
@Mixin(TaczPeekHitboxHelper.class)
public class MixinTaczPeekHeadshotLimit {
    @Inject(method = "getHitResult", at = @At("RETURN"), cancellable = true, remap = false)
    private static void taczfixes$limitPeekHeadshot(Projectile projectile, Entity entity, Vec3 startVec, Vec3 endVec, CallbackInfoReturnable<EntityResult> cir) {
        EntityResult result = cir.getReturnValue();
        if (result == null || !result.isHeadshot()) {
            return;
        }
        if (PeekHeadshotHelper.shouldDemoteToBodyShot(entity, result.getHitPos())) {
            cir.setReturnValue(new EntityResult(result.getEntity(), result.getHitPos(), false));
        }
    }
}