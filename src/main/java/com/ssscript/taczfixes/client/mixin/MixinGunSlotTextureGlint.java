package com.ssscript.taczfixes.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.client.render.GunSlotGlintContext;
import com.ssscript.taczfixes.client.render.SlotGlintRenderType;
import com.ssscript.taczfixes.client.render.SlotGlintVertexConsumer;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GunItemRendererWrapper.class, remap = false)
public abstract class MixinGunSlotTextureGlint {
    @WrapOperation(method = "renderSlotTexture",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/client/model/SlotModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            require = 1, remap = false)
    private static void taczfixes$renderSlotGlint(SlotModel model, PoseStack poseStack,
                                                   VertexConsumer consumer, int light, int overlay,
                                                   int color, Operation<Void> original,
                                                   @Local(argsOnly = true) MultiBufferSource source) {
        original.call(model, poseStack, consumer, light, overlay, color);
        if (!GunSlotGlintContext.isActive() || !(source instanceof MultiBufferSource.BufferSource buffers)) {
            return;
        }
        buffers.endBatch();
        MultiBufferSource.BufferSource glintSource = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
        VertexConsumer glint = new SlotGlintVertexConsumer(glintSource.getBuffer(SlotGlintRenderType.get()));
        model.renderToBuffer(poseStack, glint, light, overlay, color);
        glintSource.endBatch();
    }
}
