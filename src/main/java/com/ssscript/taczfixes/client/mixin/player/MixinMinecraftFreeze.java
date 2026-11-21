package com.ssscript.taczfixes.client.mixin.player;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 常凪冻结: 丢弃鼠标/按键触发的攻击、使用、持续攻击与按键绑定。 */
@Mixin(Minecraft.class)
public class MixinMinecraftFreeze {
    @Inject(method = "startAttack()Z", at = @At("HEAD"), cancellable = true)
    private void taczfixes$blockFrozenAttack(CallbackInfoReturnable<Boolean> cir) {
        if (FreezeClientState.isFrozen()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$blockFrozenUseItem(CallbackInfo ci) {
        if (FreezeClientState.isFrozen()) {
            ci.cancel();
        }
    }

    @Inject(method = "continueAttack(Z)V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$blockFrozenContinueAttack(boolean leftDown, CallbackInfo ci) {
        if (FreezeClientState.isFrozen()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleKeybinds()V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$blockFrozenKeybinds(CallbackInfo ci) {
        if (FreezeClientState.isFrozen()) {
            ci.cancel();
        }
    }
}
