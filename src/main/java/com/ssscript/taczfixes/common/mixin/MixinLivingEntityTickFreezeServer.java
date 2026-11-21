package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.handler.GunEnchantmentHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 常凪冻结: 服务端直接跳过被冻结实体的 tick。
 *
 * <p>这是让动画/声音真正静止的关键: 实体不 tick 就不会触发 AI、动画事件包(挥手/攻击)、
 * 环境音与移动, 客户端也不会收到任何会改变其姿态/状态的数据包, 不会出现抽搐。</p>
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntityTickFreezeServer {
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$freezeServerTick(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide && GunEnchantmentHandler.isFrozen(self)) {
            self.setDeltaMovement(0.0D, 0.0D, 0.0D);
            self.fallDistance = 0.0F;
            ci.cancel();
        }
    }
}