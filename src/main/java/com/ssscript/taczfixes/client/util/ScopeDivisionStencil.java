package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ssscript.taczfixes.client.mixin.MixinBedrockAttachmentModelScopeSuppress;
import com.ssscript.taczfixes.client.render.PbrBloom;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.lwjgl.opengl.GL11;

public final class ScopeDivisionStencil {
    private static final RenderType STENCIL_TYPE = RenderType.create(
            "taczfixes_scope_division_stencil",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            262144, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorTexLightmapShader))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(false, false))
                    .createCompositeState(false));

    private ScopeDivisionStencil() {
    }

    public static void mark(BedrockAttachmentModel model, PoseStack poseStack,
                            ItemDisplayContext displayContext, int light, int overlay,
                            List<BedrockPart> path, MultiBufferSource buffers) {
        if (!PbrBloom.pureScopeAiming() || path == null || path.isEmpty()) {
            return;
        }
        boolean stencilWas = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        int stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        int stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        int stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        int stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        int stencilDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        int stencilDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        int stencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        try {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 255);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            GL11.glStencilMask(255);
            MultiBufferSource.BufferSource batch = Minecraft.getInstance().renderBuffers().bufferSource();
            ((MixinBedrockAttachmentModelScopeSuppress) model)
                    .taczfixes$renderTempPart(poseStack, displayContext, STENCIL_TYPE, light, overlay, path, batch);
            batch.endBatch(STENCIL_TYPE);
        } finally {
            GL11.glStencilMask(stencilWriteMask);
            if (stencilWas) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
                GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask);
                GL11.glStencilOp(stencilFail, stencilDepthFail, stencilDepthPass);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
        }
    }
}
