package com.ssscript.taczfixes.client.mixin.entity;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 常凪冻结: 本机玩家自己重写了 tick, 需要单独拦截(冻结自己画面里的动画与本地音效)。 */
@Mixin(LocalPlayer.class)
public class MixinLocalPlayerTickFreeze {
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$freezeClientTick(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (!self.level().isClientSide || !FreezeClientState.isEntityFrozen(self)) {
            return;
        }
        self.setOldPosAndRot();
        self.setDeltaMovement(0.0D, 0.0D, 0.0D);
        ci.cancel();
    }
}
