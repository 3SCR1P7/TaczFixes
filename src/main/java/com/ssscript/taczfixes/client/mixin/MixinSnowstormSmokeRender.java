package com.ssscript.taczfixes.client.mixin;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleDescription;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render.ParticleRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.ssscript.taczfixes.client.render.MuzzleSmokeRender;
import com.ssscript.taczfixes.client.util.MuzzleAfterSmoke;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ParticleRenderer.class, remap = false)
public class MixinSnowstormSmokeRender {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/github/mcmodderanchor/simplebedrockmodel/v1/particle/render/ParticleRenderer;getRenderType(Lcom/github/mcmodderanchor/simplebedrockmodel/v1/particle/data/ParticleDescription$Material;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private static RenderType taczfixes$smoothAfterSmoke(ParticleDescription.Material material, ResourceLocation texture,
            Operation<RenderType> original, @Local(argsOnly = true) ParticleEmitterInstance emitter) {
        if (MuzzleSmokeRender.hasHandShader() && MuzzleAfterSmoke.isAfterSmoke(emitter.getDefinition().getIdentifier())) {
            return MuzzleSmokeRender.hand(texture);
        }
        return original.call(material, texture);
    }
}
