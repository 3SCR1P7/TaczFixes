package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.util.ArcanaThermalState;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(ParticleEngine.class)
public class MixinParticleRenderHide {

    private static final Map<Class<?>, String> taczfixes$particleIds = new HashMap<>();

    @Inject(method = "makeParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("RETURN"))
    private void taczfixes$captureParticleId(ParticleOptions options, double x, double y, double z,
                                             double xSpeed, double ySpeed, double zSpeed,
                                             CallbackInfoReturnable<Particle> cir) {
        Particle particle = cir.getReturnValue();
        if (particle == null) return;
        ParticleType<?> type = options.getType();
        ResourceLocation id = ForgeRegistries.PARTICLE_TYPES.getKey(type);
        if (id != null) {
            taczfixes$particleIds.putIfAbsent(particle.getClass(), id.toString());
        }
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
            remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/client/particle/Particle;m_5744_(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    private void taczfixes$hideParticles(Particle particle, VertexConsumer vertexConsumer, Camera camera, float partialTick) {
        String id = taczfixes$particleIds.get(particle.getClass());
        if (id != null && Config.HIDE_PARTICLES_IN_ARCANA_THERMAL.get().contains(id)
                && ArcanaThermalState.isScopeViewActive()) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player != null) {
                IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
                if (gunOperator != null && gunOperator.getSynAimingProgress() > 0) {
                    return;
                }
            }
        }
        particle.render(vertexConsumer, camera, partialTick);
    }
}