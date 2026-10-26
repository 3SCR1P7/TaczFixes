package com.ssscript.taczfixes.client.mixin.model.attachment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BedrockAttachmentModel.class, remap = false)
public abstract class MixinBedrockAttachmentModelDivisionBloom {
    private static final String RENDER_TEMP_PART =
            "renderTempPart(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lnet/minecraft/client/renderer/RenderType;IILjava/util/List;F"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;)V";

    @Inject(method = RENDER_TEMP_PART, at = @At("HEAD"), remap = false)
    private void taczfixes$pushDivision(PoseStack poseStack, ItemDisplayContext displayContext,
                                        RenderType renderType, int light, int overlay,
                                        List<BedrockPart> path, float partialTick,
                                        MultiBufferSource bufferSource, CallbackInfo callback) {
        PbrRenderer.pushScopeDivision(taczfixes$isDivisionPath(path));
    }

    @Inject(method = RENDER_TEMP_PART, at = @At("RETURN"), remap = false)
    private void taczfixes$popDivision(PoseStack poseStack, ItemDisplayContext displayContext,
                                       RenderType renderType, int light, int overlay,
                                       List<BedrockPart> path, float partialTick,
                                       MultiBufferSource bufferSource, CallbackInfo callback) {
        PbrRenderer.popScopeDivision();
    }

    private boolean taczfixes$isDivisionPath(List<BedrockPart> path) {
        if (path == null) return false;
        List<List<BedrockPart>> divisions =
                ((MixinBedrockAttachmentModelScopeSuppress) (Object) this).taczfixes$divisionNodePaths();
        if (divisions == null) return false;
        for (List<BedrockPart> division : divisions) {
            if (division == path) return true;
        }
        return false;
    }
}
