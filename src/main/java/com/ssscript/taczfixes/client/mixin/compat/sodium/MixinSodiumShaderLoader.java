package com.ssscript.taczfixes.client.mixin.compat.sodium;

import com.ssscript.taczfixes.client.compat.SodiumShaderState;
import com.ssscript.taczfixes.client.render.LightManager;
import net.caffeinemc.mods.sodium.client.gl.shader.GlShader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderLoader.class, remap = false)
public abstract class MixinSodiumShaderLoader {

    @Inject(method = "loadShader", at = @At("HEAD"), remap = false)
    private static void taczfixes$pushShaderType(ShaderType type, ResourceLocation name, ShaderConstants constants,
                                                 CallbackInfo ci) {
        SodiumShaderState.push(type);
    }

    @Inject(method = "loadShader", at = @At("RETURN"), remap = false)
    private static void taczfixes$popShaderType(ShaderType type, ResourceLocation name, ShaderConstants constants,
                                                CallbackInfoReturnable<GlShader> cir) {
        SodiumShaderState.pop();
    }

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
    private static void taczfixes$injectColoredLight(ResourceLocation name, CallbackInfoReturnable<String> cir) {
        if (SodiumShaderState.vertex() && name != null && name.getPath().contains("block_layer_opaque")) {
            cir.setReturnValue(LightManager.sodiumVVSHInjection(cir.getReturnValue()));
        }
    }
}
