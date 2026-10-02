package com.ssscript.taczfixes.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ssscript.taczfixes.client.render.LightManager;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Embeddium(钠系)地形着色器注入彩色光(移植自 Shimmer, MIT)。
 * Embeddium 不在时该 mixin 由 TaczFixesMixinPlugin 跳过。
 */
@Mixin(value = ShaderLoader.class, remap = false)
public abstract class MixinEmbeddiumShaderLoader {

    @ModifyExpressionValue(method = "loadShader", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderParser;parseShader(Ljava/lang/String;Lme/jellysquid/mods/sodium/client/gl/shader/ShaderConstants;)Ljava/lang/String;"),
            remap = false)
    private static String taczfixes$injectColoredLight(String shader, ShaderType type, ResourceLocation name) {
        if (type == ShaderType.VERTEX && name != null && name.getPath().contains("block_layer_opaque")) {
            return LightManager.embeddiumVVSHInjection(shader);
        }
        return shader;
    }
}
