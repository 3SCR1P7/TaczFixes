package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.light.LightManager;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium(钠系)地形着色器程序创建后, 把彩色光 UBO(Lights/Env) 绑定到该程序
 * (移植自 Shimmer, MIT)。Embeddium 不在时由 TaczFixesMixinPlugin 跳过。
 */
@Mixin(value = ShaderChunkRenderer.class, remap = false)
public abstract class MixinEmbeddiumShaderChunkRenderer {

    @Inject(method = "createShader", at = @At("RETURN"))
    private void taczfixes$bindColoredLightUBO(String path, ChunkShaderOptions options,
                                               CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> cir) {
        GlProgram<ChunkShaderInterface> program = cir.getReturnValue();
        if (program != null && program.handle() >= 0) {
            LightManager.INSTANCE.bindRbProgram(program.handle());
        }
    }
}
