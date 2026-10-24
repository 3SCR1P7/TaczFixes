package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.util.ScopeDivisionStencil;
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
public abstract class MixinBedrockAttachmentModelDivisionStencil {
    @Inject(method = "captureLensDivision", at = @At("TAIL"), remap = false)
    private void taczfixes$markCapturedDivision(PoseStack poseStack, ItemDisplayContext displayContext,
                                                RenderType renderType, int light, int overlay,
                                                List<BedrockPart> path, MultiBufferSource buffers,
                                                CallbackInfo callback) {
        ScopeDivisionStencil.mark((BedrockAttachmentModel) (Object) this, poseStack, displayContext,
                light, overlay, path, buffers);
    }

    @Inject(method = "renderDivisionOnly", at = @At("TAIL"), remap = false)
    private void taczfixes$markRenderedDivision(PoseStack poseStack, ItemDisplayContext displayContext,
                                                RenderType renderType, int light, int overlay,
                                                MultiBufferSource buffers, CallbackInfo callback) {
        MixinBedrockAttachmentModelScopeSuppress accessor = (MixinBedrockAttachmentModelScopeSuppress) this;
        List<List<BedrockPart>> paths = accessor.taczfixes$divisionNodePaths();
        if (paths == null) {
            return;
        }
        BedrockAttachmentModel model = (BedrockAttachmentModel) (Object) this;
        for (List<BedrockPart> path : paths) {
            ScopeDivisionStencil.mark(model, poseStack, displayContext, light, overlay, path, buffers);
        }
    }
}
