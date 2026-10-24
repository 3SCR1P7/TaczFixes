package com.ssscript.taczfixes.client.mixin.pbr;

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
    }
}
