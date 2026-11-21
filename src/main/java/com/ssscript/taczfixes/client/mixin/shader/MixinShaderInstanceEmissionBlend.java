package com.ssscript.taczfixes.client.mixin.shader;

import com.ssscript.taczfixes.client.render.PbrBloom;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstanceEmissionBlend {
    @Inject(method = "apply", at = @At("TAIL"))
    private void taczfixes$applyEmissionBlend(CallbackInfo ci) {
        PbrRenderer.applyEmissionBlendOverride();
        // 着色器 apply 会把 GL_BLEND 对所有 draw buffer 重新打开; 发光 MRT 期间要把发光深度附件关回去。
        PbrBloom.applyEmissionDepthBlendFix();
    }
}
