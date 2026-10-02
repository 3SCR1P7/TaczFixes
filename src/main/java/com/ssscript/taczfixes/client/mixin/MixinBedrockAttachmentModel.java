package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.ssscript.taczfixes.client.client.DualRenderContext;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = {BedrockAttachmentModel.class}, remap = false)
public abstract class MixinBedrockAttachmentModel {
    @Unique
    private static final String TACZFIXES_RENDER_DESC =
            "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFLnet/minecraft/client/renderer/MultiBufferSource;)V";

    @Unique
    private final Map<BedrockPart, Boolean> taczfixes$ocularVisibilityBeforePhysical = new IdentityHashMap<>();

    @Redirect(method = TACZFIXES_RENDER_DESC, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemDisplayContext;firstPerson()Z", remap = true), require = ServerMessageOffhandActionResult.ACTION_RELOAD)
    private boolean dualWield$renderPhysicalOffhandOptic(ItemDisplayContext transformType) {
        return !taczfixes$isPhysicalOpticRender(transformType);
    }

    @Inject(method = TACZFIXES_RENDER_DESC, at = @At("HEAD"), remap = false)
    private void taczfixes$showPhysicalOcular(ItemStack attachmentItem, ItemStack currentGunItem, PoseStack poseStack,
                                              ItemDisplayContext transformType, RenderType renderType,
                                              int light, int overlay, float alpha, MultiBufferSource bufferSource,
                                              CallbackInfo ci) {
        MixinBedrockAttachmentModelScopeSuppress accessor = (MixinBedrockAttachmentModelScopeSuppress) (Object) this;
        if (!accessor.taczfixes$isScope() && !accessor.taczfixes$isSight()) return;
        if (!taczfixes$isPhysicalOpticRender(transformType)) return;
        List<List<BedrockPart>> ocularNodePaths = accessor.taczfixes$ocularNodePaths();
        if (ocularNodePaths == null) return;
        for (List<BedrockPart> path : ocularNodePaths) {
            if (path == null) continue;
            for (BedrockPart part : path) {
                if (!part.visible) {
                    taczfixes$ocularVisibilityBeforePhysical.put(part, Boolean.FALSE);
                    part.visible = true;
                }
            }
        }
    }

    @Inject(method = TACZFIXES_RENDER_DESC, at = @At("RETURN"), remap = false)
    private void taczfixes$restorePhysicalOcular(ItemStack attachmentItem, ItemStack currentGunItem, PoseStack poseStack,
                                                 ItemDisplayContext transformType, RenderType renderType,
                                                 int light, int overlay, float alpha, MultiBufferSource bufferSource,
                                                 CallbackInfo ci) {
        if (taczfixes$ocularVisibilityBeforePhysical.isEmpty()) return;
        for (Map.Entry<BedrockPart, Boolean> entry : taczfixes$ocularVisibilityBeforePhysical.entrySet()) {
            entry.getKey().visible = entry.getValue();
        }
        taczfixes$ocularVisibilityBeforePhysical.clear();
    }

    @Unique
    private boolean taczfixes$isPhysicalOpticRender(ItemDisplayContext transformType) {
        return DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || transformType != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }
}
