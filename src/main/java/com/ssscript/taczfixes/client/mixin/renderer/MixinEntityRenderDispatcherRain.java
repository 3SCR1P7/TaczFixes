package com.ssscript.taczfixes.client.mixin.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.ssscript.taczfixes.client.render.RainExposure;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 世界里的实体(其他玩家手里的枪、掉落物等)同样按原版降雨判定决定是否被雨淋到。 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcherRain {
    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void taczfixes$pushRainProbe(Entity entity, double x, double y, double z, float yaw,
                                         float partialTick, PoseStack poseStack,
                                         MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        PbrRenderer.pushWorldRainProbe(() -> RainExposure.entityRainedOn(entity));
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void taczfixes$popRainProbe(Entity entity, double x, double y, double z, float yaw,
                                        float partialTick, PoseStack poseStack,
                                        MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        PbrRenderer.popWorldRainProbe();
    }
}
