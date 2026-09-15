package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.DualMuzzleFlashState;
import com.ssscript.taczfixes.client.render.DualMuzzleHandScope;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {DualMuzzleFlashState.class}, remap = false)
public abstract class MixinDualMuzzleFlashState {
    @Inject(method = {"record"}, at = {@At("HEAD")}, remap = false)
    private static void dualWield$recordShootingHand(DualRenderContext.HandPhase hand, CallbackInfo callback) {
        DualMuzzleHandScope.record(hand);
    }
}
