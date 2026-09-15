package com.ssscript.taczfixes.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.functional.AttachmentRender;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.common.compat.ArcanaScopeStateBridge;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {AttachmentRender.class}, remap = false)
public abstract class MixinAttachmentRender {
    private static final String RENDER_ATTACHMENT_LAMBDA =
            "lambda$renderAttachment$0(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
            + "Lcom/tacz/guns/api/item/attachment/AttachmentType;Lnet/minecraft/resources/ResourceLocation;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;II"
            + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"
            + "Lcom/tacz/guns/client/resource/index/ClientAttachmentIndex;)V";

    @Redirect(method = {RENDER_ATTACHMENT_LAMBDA}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/resource/index/ClientAttachmentIndex;getLodModel()Lorg/apache/commons/lang3/tuple/Pair;", remap = false), remap = false)
    private static Pair<BedrockAttachmentModel, ResourceLocation> dualWield$forceThirdPersonFullAttachmentModel(
            ClientAttachmentIndex invokedIndex,
            ItemStack attachmentStack, ItemStack gunStack, AttachmentType type, ResourceLocation location,
            PoseStack poseStack, ItemDisplayContext transformType, int light, int overlay,
            MultiBufferSource.BufferSource bufferSource, ClientAttachmentIndex lambdaIndex) {
        if (transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return null;
        }
        return invokedIndex.getLodModel();
    }

    @WrapOperation(method = {RENDER_ATTACHMENT_LAMBDA}, at = {@At(value = "INVOKE", target = "Lcom/tacz/guns/client/model/BedrockAttachmentModel;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", remap = false)}, remap = false)
    private static void dualWield$renderOffhandOpticAsPhysicalModel(
            BedrockAttachmentModel model, ItemStack attachmentStack, ItemStack gunStack, PoseStack poseStack,
            ItemDisplayContext transformType, RenderType renderType, int light, int overlay, float partialTicks,
            MultiBufferSource.BufferSource callBufferSource,
            Operation<Void> original,
            ItemStack lambdaAttachmentStack, ItemStack lambdaGunStack, AttachmentType type, ResourceLocation location,
            PoseStack lambdaPoseStack, ItemDisplayContext lambdaTransformType, int lambdaLight, int lambdaOverlay,
            MultiBufferSource.BufferSource bufferSource, ClientAttachmentIndex lambdaIndex) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (!dualWield$isOffhandFirstPerson(transformType) || (!model.isScope() && !model.isSight())) {
            original.call(new Object[]{model, attachmentStack, gunStack, poseStack, transformType, renderType, Integer.valueOf(light), Integer.valueOf(overlay), Float.valueOf(partialTicks), callBufferSource});
            return;
        }
        ArcanaScopeStateBridge.Snapshot previousArcanaScope = ArcanaScopeStateBridge.capture();
        try {
            original.call(new Object[]{model, attachmentStack, gunStack, poseStack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, renderType, Integer.valueOf(light), Integer.valueOf(overlay), Float.valueOf(partialTicks), callBufferSource});
            ArcanaScopeStateBridge.restore(previousArcanaScope);
        } catch (Throwable th) {
            ArcanaScopeStateBridge.restore(previousArcanaScope);
            throw th;
        }
    }

    private static boolean dualWield$isOffhandFirstPerson(ItemDisplayContext transformType) {
        boolean firstPerson = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        if (firstPerson) {
            return DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        }
        return false;
    }
}
