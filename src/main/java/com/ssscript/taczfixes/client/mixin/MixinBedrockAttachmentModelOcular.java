package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * TaCZ 按骨名区分 scope/sight 目镜: 普通 "ocular"/"ocular_sight" 骨会被当成 sight 目镜。
 * 部分纯 scope 模型使用普通 "ocular" 骨, 在 renderScope 里就不会被绘制。
 * 纯 scope/纯 sight 模型渲染目镜 stencil 时, 临时让所有目镜骨都按当前视图类型渲染。
 */
@Mixin(value = BedrockAttachmentModel.class, remap = false)
public abstract class MixinBedrockAttachmentModelOcular {
    @Unique
    private List<Boolean> taczfixes$oldOcularFlags;

    @Inject(method = "renderOcularStencil(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIZLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), remap = false)
    private void taczfixes$renderAllOcularsHead(PoseStack poseStack, ItemDisplayContext displayContext,
                                                RenderType renderType, int light, int overlay, boolean scopeView,
                                                MultiBufferSource bufferSource, CallbackInfo ci) {
        taczfixes$oldOcularFlags = null;
        if (!scopeView) return;
        MixinBedrockAttachmentModelScopeSuppress accessor = (MixinBedrockAttachmentModelScopeSuppress) (Object) this;
        List<Boolean> flags = accessor.taczfixes$isScopeOcular();
        if (flags == null || flags.isEmpty()) return;
        // 模型没有 ocular_scope 骨时(例如只有普通 ocular), scope 视图会把所有目镜骨都跳过。
        if (flags.contains(Boolean.TRUE)) return;
        taczfixes$oldOcularFlags = flags;
        List<Boolean> all = new ArrayList<>(flags.size());
        for (int i = 0; i < flags.size(); i++) {
            all.add(Boolean.TRUE);
        }
        accessor.taczfixes$setIsScopeOcular(all);
    }

    @Inject(method = "renderOcularStencil(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIZLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("RETURN"), remap = false)
    private void taczfixes$renderAllOcularsReturn(PoseStack poseStack, ItemDisplayContext displayContext,
                                                  RenderType renderType, int light, int overlay, boolean scopeView,
                                                  MultiBufferSource bufferSource, CallbackInfo ci) {
        if (taczfixes$oldOcularFlags != null) {
            MixinBedrockAttachmentModelScopeSuppress accessor = (MixinBedrockAttachmentModelScopeSuppress) (Object) this;
            accessor.taczfixes$setIsScopeOcular(taczfixes$oldOcularFlags);
            taczfixes$oldOcularFlags = null;
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("RETURN"), remap = false)
    private void taczfixes$renderPlainOcularAfter(ItemStack gun, ItemStack ammo, PoseStack poseStack,
                                                  ItemDisplayContext displayContext, RenderType renderType,
                                                  int light, int overlay, float partialTick,
                                                  MultiBufferSource bufferSource, CallbackInfo ci) {
        if (!displayContext.firstPerson()) return;
        MixinBedrockAttachmentModelScopeSuppress accessor = (MixinBedrockAttachmentModelScopeSuppress) (Object) this;
        if (!accessor.taczfixes$isScope() || accessor.taczfixes$isSight()) return;
        List<List<BedrockPart>> paths = accessor.taczfixes$ocularNodePaths();
        List<Boolean> flags = accessor.taczfixes$isScopeOcular();
        if (paths == null || paths.isEmpty()) return;
        boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        int func = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        int ref = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        int mask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        if (stencil) GL11.glDisable(GL11.GL_STENCIL_TEST);
        try {
            for (int i = 0; i < paths.size(); i++) {
                boolean scopeOcular = flags != null && i < flags.size() && Boolean.TRUE.equals(flags.get(i));
                if (!scopeOcular) {
                    accessor.taczfixes$renderTempPart(poseStack, displayContext, renderType, light, overlay, paths.get(i));
                }
            }
        } finally {
            if (stencil) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
                GL11.glStencilFunc(func, ref, mask);
            }
        }
    }}
