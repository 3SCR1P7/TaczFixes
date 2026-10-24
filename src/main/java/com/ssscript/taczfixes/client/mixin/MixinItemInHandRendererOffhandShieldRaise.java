package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.client.DualWieldClient;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRendererOffhandShieldRaise {
    @Inject(method = "tick", at = @At("HEAD"))
    private void taczfixes$captureRenderer(CallbackInfo callback) {
        DualWieldClient.setOffhandRenderer((ItemInHandRenderer) (Object) this);
    }
}
