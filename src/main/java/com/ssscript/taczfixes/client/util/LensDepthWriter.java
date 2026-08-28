package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ssscript.taczfixes.client.mixin.MixinBedrockAttachmentModelScopeSuppress;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class LensDepthWriter {
    private static final RenderType LENS_DEPTH_RENDER_TYPE = RenderType.create(
            "taczfixes_lens_depth",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            262144, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorTexLightmapShader))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(false, true))
                    .createCompositeState(false));

    private LensDepthWriter() {
    }

    public static void writeLensDepth(ItemStack item, ItemStack gun, PoseStack poseStack,
                                      ItemDisplayContext displayContext, int light, int overlay) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        if (attachment == null) return;
        Optional<ClientAttachmentIndex> index = SwitchedDisplayManager.getClientAttachmentIndex(gun, attachment.getAttachmentId(item));
        if (!index.isPresent()) {
            index = TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(item));
        }
        index.ifPresent(indexOpt -> {
            BedrockAttachmentModel model = indexOpt.getAttachmentModel();
            if (model == null) return;
            MixinBedrockAttachmentModelScopeSuppress accessor = (MixinBedrockAttachmentModelScopeSuppress) model;
            List<List<BedrockPart>> division = accessor.taczfixes$divisionNodePaths();
            List<List<BedrockPart>> ocular = accessor.taczfixes$ocularNodePaths();
            if ((division == null || division.isEmpty()) && (ocular == null || ocular.isEmpty())) {
                return;
            }
            RenderHelper.enableItemEntityStencilTest();
            RenderSystem.stencilFunc(519, 0, 255);
            RenderSystem.stencilOp(7680, 7680, 7680);
            if (division != null) {
                for (List<BedrockPart> path : division) {
                    accessor.taczfixes$renderTempPart(poseStack, displayContext, LENS_DEPTH_RENDER_TYPE, light, overlay, path);
                }
            }
            if (ocular != null) {
                for (List<BedrockPart> path : ocular) {
                    accessor.taczfixes$renderTempPart(poseStack, displayContext, LENS_DEPTH_RENDER_TYPE, light, overlay, path);
                }
            }
            RenderSystem.stencilFunc(519, 0, 255);
            RenderHelper.disableItemEntityStencilTest();
        });
    }
}
