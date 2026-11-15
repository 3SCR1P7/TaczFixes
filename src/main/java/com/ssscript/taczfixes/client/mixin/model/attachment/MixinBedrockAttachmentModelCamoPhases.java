package com.ssscript.taczfixes.client.mixin.model.attachment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 瞄具"内部"（目镜、分划、镜片捕获）不应用动态迷彩，瞄具外壳照常保留。
 *
 * <p>不靠骨骼名字/路径判断，而是按 TaCZ 自己的渲染阶段划分：第一人称瞄具的镜内内容只在这三个方法里
 * 绘制——{@code renderOcularStencil}（目镜模板）、{@code renderDivisionOnly}（分划）、
 * {@code renderOcularAndDivision}（目镜+分划+镜片捕获）。外壳由外层 {@code renderScope/Sight/Both}
 * 的 {@code scopeBodyPath} 与最后那次 {@code super.render} 绘制，不在这三个方法里。
 * 因此只在这三个方法期间压住迷彩：任何瞄具包（骨骼命名无关）都自动满足
 * "目镜/分划不上迷彩、外壳保留迷彩"。
 *
 * <p>加速渲染下带迷彩的物品本身会退回原版路径（见 MixinARCompatGate），所以这里覆盖的是实际执行的路径。
 */
@Mixin(value = BedrockAttachmentModel.class, remap = false)
public abstract class MixinBedrockAttachmentModelCamoPhases {
    private static final String OCULAR_STENCIL =
            "renderOcularStencil(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lnet/minecraft/client/renderer/RenderType;IIZ"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;)V";
    private static final String DIVISION_ONLY =
            "renderDivisionOnly(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lnet/minecraft/client/renderer/RenderType;II"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;)V";
    private static final String OCULAR_AND_DIVISION =
            "renderOcularAndDivision(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIZ"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;)V";

    @Inject(method = {OCULAR_STENCIL, OCULAR_AND_DIVISION}, at = @At("HEAD"), remap = false)
    private void taczfixes$pushOcularSuppression(PoseStack poseStack, ItemDisplayContext transformType,
                                                 RenderType renderType, int light, int overlay, boolean flag,
                                                 MultiBufferSource bufferSource, CallbackInfo callback) {
        PbrRenderer.pushCamoSuppression();
    }

    @Inject(method = {OCULAR_STENCIL, OCULAR_AND_DIVISION}, at = @At("RETURN"), remap = false)
    private void taczfixes$popOcularSuppression(PoseStack poseStack, ItemDisplayContext transformType,
                                                RenderType renderType, int light, int overlay, boolean flag,
                                                MultiBufferSource bufferSource, CallbackInfo callback) {
        PbrRenderer.popCamoSuppression();
    }

    @Inject(method = DIVISION_ONLY, at = @At("HEAD"), remap = false)
    private void taczfixes$pushDivisionSuppression(PoseStack poseStack, ItemDisplayContext transformType,
                                                   RenderType renderType, int light, int overlay,
                                                   MultiBufferSource bufferSource, CallbackInfo callback) {
        PbrRenderer.pushCamoSuppression();
    }

    @Inject(method = DIVISION_ONLY, at = @At("RETURN"), remap = false)
    private void taczfixes$popDivisionSuppression(PoseStack poseStack, ItemDisplayContext transformType,
                                                  RenderType renderType, int light, int overlay,
                                                  MultiBufferSource bufferSource, CallbackInfo callback) {
        PbrRenderer.popCamoSuppression();
    }
}
