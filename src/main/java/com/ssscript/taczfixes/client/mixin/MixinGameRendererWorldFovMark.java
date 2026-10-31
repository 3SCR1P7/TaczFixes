package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.handler.ScopeFovTransitionHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 标记 GameRenderer#renderLevel 即将进行的世界层 getFov 调用, 供 FOV 过渡只作用于世界层。 */
@Mixin(GameRenderer.class)
public class MixinGameRendererWorldFovMark {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void taczfixes$markWorldFov(DeltaTracker deltaTracker, CallbackInfo ci) {
        ScopeFovTransitionHandler.markWorldFovCall();
    }
}
