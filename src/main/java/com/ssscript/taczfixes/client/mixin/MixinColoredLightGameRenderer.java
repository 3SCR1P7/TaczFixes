package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.light.LightManager;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 着色器重载后重新创建/绑定彩色光 UBO(移植自 Shimmer, MIT)。
 */
@Mixin(GameRenderer.class)
public abstract class MixinColoredLightGameRenderer {

    @Inject(method = "reloadShaders", at = @At("RETURN"))
    private void taczfixes$reloadColoredLightShaders(ResourceProvider provider, CallbackInfo ci) {
        LightManager.INSTANCE.reloadShaders();
    }
}
