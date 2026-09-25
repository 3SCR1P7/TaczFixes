package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.ScopeFovDebug;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 记录 getFov 两种调用的最终返回值, 供镜内/外 FOV 诊断日志使用。 */
@Mixin(GameRenderer.class)
public class MixinGameRendererFovCapture {

    @Inject(method = "m_109141_", at = @At("RETURN"))
    private void taczfixes$captureFov(Camera camera, float partialTicks, boolean useFovSetting,
                                      CallbackInfoReturnable<Double> cir) {
        ScopeFovDebug.capture(useFovSetting, cir.getReturnValue());
    }
}
