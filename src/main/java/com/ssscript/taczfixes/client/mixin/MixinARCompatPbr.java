package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.pbr.PbrRenderer;
import com.tacz.guns.compat.ar.ARCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps TaCZ's model, attachment stencil and beam paths on the same pipeline. */
@Mixin(value = ARCompat.class, remap = false)
public abstract class MixinARCompatPbr {
    @Inject(method = "shouldAccelerate()Z", at = @At("HEAD"), cancellable = true)
    private static void taczfixes$pbrConventionalPipeline(CallbackInfoReturnable<Boolean> cir) {
        // ARCompat ships with TaCZ even when Accelerated Rendering is absent.
        // AR's BufferBuilder still accepts ordinary vertices; TaCZ enters its
        // mesh fast path only when shouldAccelerate() is true. Bypassing it lets
        // PbrType.end draw and capture emission with the correct stencil state.
        // Other mods' AR paths and the global pipeline stacks remain untouched.
        if (ARCompat.LOADED && PbrRenderer.enabled()) cir.setReturnValue(false);
    }
}
