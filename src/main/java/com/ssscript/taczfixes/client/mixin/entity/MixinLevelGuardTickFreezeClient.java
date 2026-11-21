package com.ssscript.taczfixes.client.mixin.entity;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 常凪冻结: 客户端同样在实体 tick 的唯一入口(guardEntityTick)跳过被冻结实体,
 * 覆盖像监守者这类自己重写了 tick() 的实体, 避免动画状态继续更新导致抽搐。
 */
@Mixin(Level.class)
public class MixinLevelGuardTickFreezeClient {
    @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
    private void taczfixes$skipFrozenEntityTick(Consumer<Entity> tick, Entity entity, CallbackInfo ci) {
        if (entity != null && entity.level().isClientSide && FreezeClientState.isEntityFrozen(entity)) {
            ci.cancel();
        }
    }
}