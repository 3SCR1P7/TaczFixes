package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.handler.GunEnchantmentHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 常凪冻结: 在实体 tick 的唯一入口(guardEntityTick)直接跳过被冻结实体的 tick。
 *
 * <p>只在 LivingEntity#tick 上取消是不够的: 像监守者(Warden)这类自己重写了 tick() 的实体,
 * 覆盖方法里的动画状态更新仍会执行, 表现为动画在两帧之间反复横跳。这里从入口跳过,
 * 任何实体(含重写 tick 的、模组实体)都不会再 tick。</p>
 */
@Mixin(Level.class)
public class MixinLevelGuardTickFreezeServer {
    @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
    private void taczfixes$skipFrozenEntityTick(Consumer<Entity> tick, Entity entity, CallbackInfo ci) {
        if (entity != null && !entity.level().isClientSide && GunEnchantmentHandler.isFrozen(entity)) {
            ci.cancel();
        }
    }
}