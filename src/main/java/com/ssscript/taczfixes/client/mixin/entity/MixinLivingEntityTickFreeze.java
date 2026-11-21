package com.ssscript.taczfixes.client.mixin.entity;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 常凪冻结: 客户端跳过被冻结实体的 tick。
 *
 * <p>不清空任何动画状态: 冻结时实体就停在当时的姿态上(tick 不再推进, 渲染的 partialTick
 * 也被 MixinLivingEntityRendererFreezePartial 固定), 不改变原本的观感。</p>
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntityTickFreeze {
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$freezeClientTick(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide || !FreezeClientState.isEntityFrozen(self)) {
            return;
        }
        self.setOldPosAndRot();
        self.setDeltaMovement(0.0D, 0.0D, 0.0D);
        ci.cancel();
    }
}