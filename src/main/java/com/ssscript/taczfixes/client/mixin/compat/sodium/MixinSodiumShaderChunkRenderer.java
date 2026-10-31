package com.ssscript.taczfixes.client.mixin.compat.sodium;

import com.ssscript.taczfixes.client.render.LightManager;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderChunkRenderer.class, remap = false)
public abstract class MixinSodiumShaderChunkRenderer {

    @Inject(method = "createShader", at = @At("RETURN"), remap = false)
    private void taczfixes$bindColoredLightUBO(String path, ChunkShaderOptions options,
                                               CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> cir) {
        GlProgram<ChunkShaderInterface> program = cir.getReturnValue();
        if (program != null && program.handle() >= 0) {
            LightManager.INSTANCE.bindRbProgram(program.handle());
        }
    }
}
