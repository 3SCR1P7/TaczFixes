package com.ssscript.taczfixes.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.compat.ArcanaThermalState;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ParticleEngine.class)
public class MixinParticleRenderHide {

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
            remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/client/particle/Particle;m_5744_(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    private void taczfixes$hideParticles(Particle particle, VertexConsumer vertexConsumer, Camera camera, float partialTick) {
        if (Config.HIDE_PARTICLES_IN_ARCANA_THERMAL.get().contains(particle.getClass().getName())
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
