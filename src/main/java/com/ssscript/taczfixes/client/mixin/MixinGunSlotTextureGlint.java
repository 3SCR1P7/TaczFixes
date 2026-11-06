package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.client.render.GunSlotGlintContext;
import com.ssscript.taczfixes.client.render.SlotGlintVertexConsumer;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GunItemRendererWrapper.class, remap = false)
public abstract class MixinGunSlotTextureGlint {
    private static final ThreadLocal<MultiBufferSource.BufferSource> SLOT_GLINT_SOURCE =
            ThreadLocal.withInitial(() -> MultiBufferSource.immediate(new BufferBuilder(1536)));
    private static SlotModel taczfixes$slotModel;

    @Inject(method = "renderSlotTexture", at = @At("RETURN"), remap = false)
    private static void taczfixes$renderSlotGlint(PoseStack poseStack, MultiBufferSource source,
                                                   int light, int overlay, ResourceLocation texture,
                                                   CallbackInfo ci) {
        if (!GunSlotGlintContext.isActive() || !(source instanceof MultiBufferSource.BufferSource buffers)) {
            return;
        }
        if (taczfixes$slotModel == null) {
            taczfixes$slotModel = new SlotModel();
        }
        buffers.endBatch();
        MultiBufferSource.BufferSource glintSource = SLOT_GLINT_SOURCE.get();
        VertexConsumer glint = new SlotGlintVertexConsumer(glintSource.getBuffer(RenderType.glint()));
        taczfixes$slotModel.renderToBuffer(poseStack, glint, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        glintSource.endBatch();
    }
}
