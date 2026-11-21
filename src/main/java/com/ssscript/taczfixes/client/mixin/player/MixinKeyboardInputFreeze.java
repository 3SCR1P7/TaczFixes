package com.ssscript.taczfixes.client.mixin.player;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 常凪冻结: 键盘输入全部丢弃(移动/跳跃/潜行/冲刺)。 */
@Mixin(KeyboardInput.class)
public class MixinKeyboardInputFreeze {
    @Inject(method = "tick(ZF)V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$blockFrozenInput(boolean isMoving, float amount, CallbackInfo ci) {
        if (!FreezeClientState.isFrozen()) {
            return;
        }
        Input input = (Input) (Object) this;
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
        ci.cancel();
    }
}
