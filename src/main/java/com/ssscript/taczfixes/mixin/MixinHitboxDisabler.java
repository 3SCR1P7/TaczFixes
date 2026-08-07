package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.Config;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class MixinHitboxDisabler {
    @Shadow
    private boolean renderHitBoxes;

    @Inject(method = "render", at = @At("HEAD"))
    private void taczfixes$disableHitboxes(CallbackInfo ci) {
        if (Config.DISABLE_HITBOXES.get()) {
            this.renderHitBoxes = false;
        }
    }
}
