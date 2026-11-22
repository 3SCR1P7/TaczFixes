package com.ssscript.taczfixes.client.mixin.shader;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.render.LightManager;
import com.ssscript.taczfixes.client.render.ShaderSourceValidator;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Embeddium(钠系)地形着色器注入彩色光(移植自 Shimmer, MIT)。
 * Embeddium 不在时该 mixin 由 TaczFixesMixinPlugin 跳过。
 * 注入是"尽力而为": 注入后先试编译, 不通过就原样放回, 不会把渲染管线改崩。
 */
@Mixin(value = ShaderLoader.class, remap = false)
public abstract class MixinEmbeddiumShaderLoader {

    @ModifyExpressionValue(method = "loadShader", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderParser;parseShader(Ljava/lang/String;Lme/jellysquid/mods/sodium/client/gl/shader/ShaderConstants;)Ljava/lang/String;"),
            remap = false)
    private static String taczfixes$injectColoredLight(String shader, ShaderType type, ResourceLocation name) {
        if (type != ShaderType.VERTEX || name == null || !name.getPath().contains("block_layer_opaque")) {
            return shader;
        }

        String injected;
        try {
            injected = LightManager.embeddiumVVSHInjection(shader);
        } catch (Throwable t) {
            TaczFixesMod.LOGGER.debug("Could not inject colored light into sodium shader {}", name, t);
            return shader;
        }
        if (injected == null || injected.equals(shader)) {
            return shader;
        }
        if (ShaderSourceValidator.compiles(GL20.GL_VERTEX_SHADER, List.of(injected))) {
            return injected;
        }
        TaczFixesMod.LOGGER.debug("Discarding colored light injection for sodium shader {} because the driver rejected it", name);
        return shader;
    }
}
