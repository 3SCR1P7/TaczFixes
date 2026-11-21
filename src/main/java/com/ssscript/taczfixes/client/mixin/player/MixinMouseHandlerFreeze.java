package com.ssscript.taczfixes.client.mixin.player;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 常凪冻结: 丢弃鼠标转向、按键与滚轮输入。 */
@Mixin(MouseHandler.class)
public class MixinMouseHandlerFreeze {
    @Inject(method = "turnPlayer()V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$blockFrozenLook(CallbackInfo ci) {
        if (FreezeClientState.isFrozen()) {
            ci.cancel();
        }
    }

    @Inject(method = "onPress(JIII)V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$blockFrozenMousePress(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (FreezeClientState.isFrozen()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$blockFrozenScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (FreezeClientState.isFrozen()) {
            ci.cancel();
        }
    }
}
