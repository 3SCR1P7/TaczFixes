package com.ssscript.taczfixes.client.mixin.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.ssscript.taczfixes.client.render.RainExposure;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 方块实体渲染前用原版降雨判定算好"这个方块是否露天淋雨", 供 PBR 湿润效果使用。
 * 判定结果在提交几何时固化进 RenderType 变体, 绘制延后也不影响。
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MixinBlockEntityRenderDispatcherRain {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", at = @At("HEAD"))
    private void taczfixes$pushRainProbe(BlockEntity blockEntity, float partialTick, PoseStack poseStack,
                                         MultiBufferSource bufferSource, CallbackInfo ci) {
        PbrRenderer.pushWorldRainProbe(() -> RainExposure.blockEntityRainedOn(blockEntity));
    }

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", at = @At("RETURN"))
    private void taczfixes$popRainProbe(BlockEntity blockEntity, float partialTick, PoseStack poseStack,
                                        MultiBufferSource bufferSource, CallbackInfo ci) {
        PbrRenderer.popWorldRainProbe();
    }
}
