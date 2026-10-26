package com.ssscript.taczfixes.client.mixin.shader;

import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.AcceleratedBufferSource;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AcceleratedBufferSource.class, remap = false)
public abstract class MixinAcceleratedBufferSourcePbr {

    @Inject(method = "drawBuffers", at = @At("HEAD"), remap = false)
    private void taczfixes$beginAcceleratedPass(CallbackInfo ci) {
        PbrRenderer.beginAcceleratedPass();
    }

    @Inject(method = "drawBuffers", at = @At("RETURN"), remap = false)
    private void taczfixes$endAcceleratedPass(CallbackInfo ci) {
        PbrRenderer.endAcceleratedPass();
    }
}
