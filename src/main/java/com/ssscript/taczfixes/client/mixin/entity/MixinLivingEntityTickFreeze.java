package com.ssscript.taczfixes.client.mixin.entity;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 常凪冻结: 客户端暂停该实体的 tick, 并清空动画状态, 从而彻底静止(不抽搐)。 */
@Mixin(LivingEntity.class)
public class MixinLivingEntityTickFreeze {
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$freezeClientTick(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide || !FreezeClientState.isEntityFrozen(self)) {
            return;
        }
        FreezeClientState.freezePose(self);
        ci.cancel();
    }
}