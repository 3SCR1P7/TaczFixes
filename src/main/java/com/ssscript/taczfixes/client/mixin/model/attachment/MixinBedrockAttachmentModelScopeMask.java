package com.ssscript.taczfixes.client.mixin.model.attachment;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ssscript.taczfixes.client.client.DualRenderContext;
import com.ssscript.taczfixes.client.render.PbrBloom;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.util.List;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BedrockAttachmentModel.class, remap = false)
public abstract class MixinBedrockAttachmentModelScopeMask {
    private static final String RENDER =
            "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lnet/minecraft/client/renderer/RenderType;IIF"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;)V";

    @Unique
    private static RenderType taczfixes$maskRenderType;

    @Inject(method = RENDER, at = @At("TAIL"), remap = false)
    private void taczfixes$captureScopeMask(ItemStack attachment, ItemStack gun, PoseStack poseStack,
                                            ItemDisplayContext displayContext, RenderType renderType,
                                            int light, int overlay, float partialTick,
                                            MultiBufferSource bufferSource, CallbackInfo callback) {
        BedrockAttachmentModel model = (BedrockAttachmentModel) (Object) this;
        if (displayContext != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) return;
        if (DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND) return;
        if ((!model.isScope() && !model.isSight()) || !PbrBloom.pureScopeAiming()) return;
        MixinBedrockAttachmentModelScopeSuppress accessor = (MixinBedrockAttachmentModelScopeSuppress) (Object) this;
        if (!PbrBloom.isActiveScopeItem(accessor.taczfixes$attachmentItem(), gun)) return;
        List<List<BedrockPart>> divisions = accessor.taczfixes$divisionNodePaths();
        if (divisions != null && !divisions.isEmpty()) {
            taczfixes$markDivisions(accessor, divisions, poseStack, displayContext, light, overlay, bufferSource);
        }
        PbrBloom.captureScopeMask();
    }

    @Unique
    private static void taczfixes$markDivisions(MixinBedrockAttachmentModelScopeSuppress accessor,
                                                List<List<BedrockPart>> divisions, PoseStack poseStack,
                                                ItemDisplayContext displayContext, int light, int overlay,
                                                MultiBufferSource bufferSource) {
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        int stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        int stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        int stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        int stencilDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        int stencilDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        int stencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var colorMask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            boolean red = colorMask.get(0) != 0;
            boolean green = colorMask.get(1) != 0;
            boolean blue = colorMask.get(2) != 0;
            boolean alpha = colorMask.get(3) != 0;
            try {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_STENCIL_TEST);
                GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 255);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
                GL11.glStencilMask(255);
                GL11.glColorMask(false, false, false, false);
                GL11.glDepthMask(false);
                RenderType type = taczfixes$maskRenderType();
                for (List<BedrockPart> path : divisions) {
                    accessor.taczfixes$renderTempPart(poseStack, displayContext, type, light, overlay, path, bufferSource);
                }
            } finally {
                GL11.glColorMask(red, green, blue, alpha);
                GL11.glDepthMask(depthMask);
                GL11.glStencilMask(stencilWriteMask);
                if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
                if (stencil) {
                    GL11.glEnable(GL11.GL_STENCIL_TEST);
                    GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask);
                    GL11.glStencilOp(stencilFail, stencilDepthFail, stencilDepthPass);
                } else {
                    GL11.glDisable(GL11.GL_STENCIL_TEST);
                }
            }
        }
    }

    @Unique
    private static RenderType taczfixes$maskRenderType() {
        if (taczfixes$maskRenderType == null) {
            taczfixes$maskRenderType = RenderType.create(
                    "taczfixes_scope_division_mask",
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                    VertexFormat.Mode.QUADS,
                    262144, false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(
                                    GameRenderer::getPositionColorTexLightmapShader))
                            .setDepthTestState(new RenderStateShard.DepthTestStateShard("always", GL11.GL_ALWAYS))
                            .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(false, false))
                            .createCompositeState(false));
        }
        return taczfixes$maskRenderType;
    }
}
