package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.ssscript.taczfixes.common.data.GunPosAlterManager;
import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.AttachmentRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StandbySlotBuffer {
    public record PendingSlot(ItemStack item, BedrockPart node, String slotId,
                              List<CustomSlotMount.Step> chain, boolean attachmentNode) {
    }

    private static List<PendingSlot> pending = Collections.emptyList();

    private StandbySlotBuffer() {
    }

    public static void setPending(List<PendingSlot> standby) {
        pending = standby;
    }

    public static List<PendingSlot> takePending() {
        List<PendingSlot> list = pending;
        pending = Collections.emptyList();
        return list;
    }

    public static void renderSlotAttachment(ItemStack item, ItemStack gun, BedrockPart node,
                                            PoseStack poseStack, ItemDisplayContext displayContext,
                                            int light, int overlay, String slotId) {
        renderSlotAttachment(item, gun, node, poseStack, displayContext, light, overlay, slotId,
                Collections.emptyList());
    }

    public static void renderSlotAttachment(ItemStack item, ItemStack gun, BedrockPart node,
                                            PoseStack poseStack, ItemDisplayContext displayContext,
                                            int light, int overlay, String slotId,
                                            List<CustomSlotMount.Step> chain) {
        renderSlotAttachment(item, gun, node, poseStack, displayContext, light, overlay, slotId, chain, false);
    }

    public static void renderSlotAttachment(ItemStack item, ItemStack gun, BedrockPart node,
                                            PoseStack poseStack, ItemDisplayContext displayContext,
                                            int light, int overlay, String slotId,
                                            List<CustomSlotMount.Step> chain, boolean attachmentNode) {
        poseStack.pushPose();
        CustomSlotMount.apply(poseStack, chain, gun, displayContext, light, overlay);
        if (attachmentNode) {
            // 配件模型内的定位组自动补 1.5 格(等价于把 pivot 上调 24 单位; 该位姿空间 Y 反向)
            poseStack.translate(0.0F, -1.5F, 0.0F);
        }
        applyNodePathTransform(node, poseStack);
        applyPosAlter(node, gun, poseStack, slotId);
        applySlotAdapterOffset(item, gun, slotId, poseStack, displayContext, light, overlay);
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) {
            poseStack.popPose();
            return;
        }
        com.tacz.guns.api.item.attachment.AttachmentType type = ia.getType(item);
        if (type == null || type == com.tacz.guns.api.item.attachment.AttachmentType.NONE) {
            type = com.tacz.guns.api.item.attachment.AttachmentType.SCOPE;
        }
        AttachmentRender.renderAttachment(item, gun, type, poseStack, displayContext, light, overlay);
        poseStack.popPose();
    }

    public static void ensureScopeOcularVisible(BedrockAttachmentModel model,
                                                java.util.Map<BedrockPart, Boolean> restore) {
        try {
            com.ssscript.taczfixes.client.mixin.model.attachment.MixinBedrockAttachmentModelScopeSuppress acc =
                    (com.ssscript.taczfixes.client.mixin.model.attachment.MixinBedrockAttachmentModelScopeSuppress) model;
            List<List<BedrockPart>> ocular = acc.taczfixes$ocularNodePaths();
            List<Boolean> isScopeOcular = acc.taczfixes$isScopeOcular();
            if (ocular == null) return;
            boolean pureScope = model.isScope() && !model.isSight();
            boolean pureSight = model.isSight() && !model.isScope();
            for (int i = 0; i < ocular.size(); i++) {
                boolean scopeType = isScopeOcular != null && i < isScopeOcular.size() && isScopeOcular.get(i);
                List<BedrockPart> path = ocular.get(i);
                if (path == null || path.isEmpty()) continue;
                boolean wantVisible = pureScope || (!pureSight && scopeType);
                for (BedrockPart p : path) {
                    if (p.visible != wantVisible) {
                        restore.putIfAbsent(p, p.visible);
                        p.visible = wantVisible;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** standby 瞄具物理渲染: 传非第一人称 context, 让 TaCZ/Arcana 都走物理分支(不写 stencil、不 capture 镜片)。 */
    public static void renderOpticPhysical(BedrockAttachmentModel model, ItemStack item, ItemStack gun,
                                           PoseStack poseStack, ItemDisplayContext displayContext,
                                           RenderType renderType, int light, int overlay) {
        if (model == null) return;
        java.util.Map<BedrockPart, Boolean> restore = new java.util.LinkedHashMap<>();
        applyStandbyOpticVisibility(model, restore);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        ItemDisplayContext physicalContext = displayContext.firstPerson()
                ? ItemDisplayContext.FIXED : displayContext;
        // 标记 standby 物理渲染: 否则 MixinBedrockAttachmentModel 会把所有 ocular(含 sight 目镜)强制设为可见。
        boolean previousStandbyPass = ScopeCullHelper.isStandbyOpticPass();
        ScopeCullHelper.setStandbyOpticPass(true);
        try {
            model.render(item, gun, poseStack, physicalContext, renderType, light, overlay, 1.0f, bufferSource);
        } finally {
            ScopeCullHelper.setStandbyOpticPass(previousStandbyPass);
            restoreOcularVisibility(restore);
        }
    }

    /** 标准槽瞄具作为 standby 时物理渲染: 保留 AttachmentRender 的原生槽位/适配器变换, 但用非第一人称 context。 */
    public static void renderSlotAttachmentPhysical(ItemStack item, ItemStack gun, AttachmentType type,
                                                    PoseStack poseStack, ItemDisplayContext displayContext,
                                                    int light, int overlay) {
        java.util.Map<BedrockPart, Boolean> restore = new java.util.LinkedHashMap<>();
        BedrockAttachmentModel model = resolveAttachmentModel(item);
        if (model != null) applyStandbyOpticVisibility(model, restore);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        ItemDisplayContext physicalContext = displayContext.firstPerson()
                ? ItemDisplayContext.FIXED : displayContext;
        boolean previousStandbyPass = ScopeCullHelper.isStandbyOpticPass();
        ScopeCullHelper.setStandbyOpticPass(true);
        try {
            AttachmentRender.renderAttachment(item, gun, type, poseStack, physicalContext, light, overlay, bufferSource);
        } finally {
            ScopeCullHelper.setStandbyOpticPass(previousStandbyPass);
            restoreOcularVisibility(restore);
        }
    }

    /** standby 瞄具可见性: 隐藏 division; 纯 scope 显示全部 ocular, 组合只显示 scope ocular, 纯 sight 隐藏全部。 */
    public static void applyStandbyOpticVisibility(BedrockAttachmentModel model,
                                                   java.util.Map<BedrockPart, Boolean> restore) {
        if (model == null) return;
        com.ssscript.taczfixes.client.mixin.model.attachment.MixinBedrockAttachmentModelScopeSuppress acc =
                (com.ssscript.taczfixes.client.mixin.model.attachment.MixinBedrockAttachmentModelScopeSuppress) model;
        boolean pureScope = acc.taczfixes$isScope() && !acc.taczfixes$isSight();
        boolean pureSight = acc.taczfixes$isSight() && !acc.taczfixes$isScope();
        List<List<BedrockPart>> divisions = acc.taczfixes$divisionNodePaths();
        if (divisions != null) {
            for (List<BedrockPart> path : divisions) setPathVisible(restore, path, false);
        }
        List<List<BedrockPart>> oculars = acc.taczfixes$ocularNodePaths();
        List<Boolean> flags = acc.taczfixes$isScopeOcular();
        if (oculars != null) {
            for (int i = 0; i < oculars.size(); i++) {
                boolean scopeOcular = flags != null && i < flags.size() && Boolean.TRUE.equals(flags.get(i));
                boolean visible = pureScope || (!pureSight && scopeOcular);
                setPathVisible(restore, oculars.get(i), visible);
            }
        }
    }

    private static void setPathVisible(java.util.Map<BedrockPart, Boolean> restore,
                                       List<BedrockPart> path, boolean visible) {
        if (path == null) return;
        for (BedrockPart part : path) {
            if (part.visible != visible) {
                restore.putIfAbsent(part, part.visible);
                part.visible = visible;
            }
        }
    }

    private static BedrockAttachmentModel resolveAttachmentModel(ItemStack item) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        if (attachment == null) return null;
        ResourceLocation id = attachment.getAttachmentId(item);
        if (id == null) return null;
        return TimelessAPI.getClientAttachmentIndex(id)
                .map(index -> index.getAttachmentModel()).orElse(null);
    }

    /** 按记录的原值恢复可见性(同一部件多次改动也只恢复一次)。 */
    public static void restoreOcularVisibility(java.util.Map<BedrockPart, Boolean> restore) {
        for (java.util.Map.Entry<BedrockPart, Boolean> entry : restore.entrySet()) {
            entry.getKey().visible = entry.getValue();
        }
        restore.clear();
    }

    /** 兼容只有普通 ocular 骨的 scope 模型: 在模型自身渲染后补画非 scope 目镜路径。 */
    public static void renderPlainOcular(BedrockAttachmentModel model, PoseStack poseStack,
                                         ItemDisplayContext displayContext, RenderType renderType,
                                         int light, int overlay) {
        if (model == null) return;
        com.ssscript.taczfixes.client.mixin.model.attachment.MixinBedrockAttachmentModelScopeSuppress acc =
                (com.ssscript.taczfixes.client.mixin.model.attachment.MixinBedrockAttachmentModelScopeSuppress) model;
        if (!acc.taczfixes$isScope() || acc.taczfixes$isSight()) return;
        List<List<BedrockPart>> paths = acc.taczfixes$ocularNodePaths();
        List<Boolean> flags = acc.taczfixes$isScopeOcular();
        if (paths == null || paths.isEmpty()) return;
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean stencil = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
        int func = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_STENCIL_FUNC);
        int ref = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_STENCIL_REF);
        int mask = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_STENCIL_VALUE_MASK);
        if (stencil) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
        try {
            for (int i = 0; i < paths.size(); i++) {
                boolean scopeOcular = flags != null && i < flags.size() && Boolean.TRUE.equals(flags.get(i));
                if (!scopeOcular) {
                    acc.taczfixes$renderTempPart(poseStack, displayContext, renderType, light, overlay, paths.get(i), bufferSource);
                }
            }
        } finally {
            if (stencil) {
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
                org.lwjgl.opengl.GL11.glStencilFunc(func, ref, mask);
            }
        }
    }
    public static void applySlotAdapterOffset(ItemStack item, ItemStack gun, String slotId,
                                              PoseStack poseStack, ItemDisplayContext displayContext,
                                              int light, int overlay) {
        if (slotId == null || slotId.isEmpty()) return;
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return;
        ResourceLocation attachmentId = ia.getAttachmentId(item);
        if (attachmentId == null) return;
        ResourceLocation adapterId = com.ssscript.taczfixes.common.util.CustomSlotStorage.getAdapter(gun, slotId);
        if (adapterId == null) return;
        if (!TimelessAPI.getCommonSlotAdapterIndex(adapterId)
                .map(index -> index.allowsAttachment(attachmentId)).orElse(false)) {
            return;
        }
        TimelessAPI.getClientSlotAdapterIndex(adapterId).ifPresent(adapterIndex -> {
            BedrockAttachmentModel adapterModel = adapterIndex.getAdapterModel();
            ResourceLocation adapterTexture = adapterIndex.getModelTexture();
            if (adapterModel != null && adapterTexture != null) {
                PbrRenderer.pushMaterialFallbackSuppression();
                try {
                    adapterModel.render(ItemStack.EMPTY, gun, poseStack,
                            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                            RenderType.entityCutout(adapterTexture), light, overlay);
                } finally {
                    PbrRenderer.popMaterialFallbackSuppression();
                }
            }
            org.joml.Vector3f mountOffset = adapterIndex.getMountOffset();
            if (mountOffset != null) {
                poseStack.translate(mountOffset.x / 16.0F, -mountOffset.y / 16.0F, mountOffset.z / 16.0F);
            }
        });
    }

    public static void applyPosAlter(BedrockPart node, ItemStack gun, PoseStack pose) {
        applyPosAlter(node, gun, pose, null);
    }

    public static void applyPosAlter(BedrockPart node, ItemStack gun, PoseStack pose, String slotKey) {
        if (gun == null || gun.isEmpty()) return;
        String key = slotKey;
        if (key == null || key.isEmpty()) {
            if (node == null || node.name == null || !node.name.endsWith("_pos")) return;
            key = node.name.substring(0, node.name.length() - "_pos".length());
        }
        if (!GunPosAlterManager.isEnabled(gun, key)) return;
        float z = PosAlterStorage.get(gun, key);
        if (z != 0.0F) {
            pose.translate(0.0F, 0.0F, z / 16.0F);
        }
    }

    public static void applyNodePathTransform(BedrockPart node, PoseStack pose) {
        List<BedrockPart> path = new ArrayList<>();
        BedrockPart cur = node;
        while (cur != null) {
            path.add(cur);
            cur = cur.getParent();
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            path.get(i).translateAndRotateAndScale(pose);
        }
    }
}
