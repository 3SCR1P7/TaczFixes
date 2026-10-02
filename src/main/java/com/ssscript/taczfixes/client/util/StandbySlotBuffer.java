package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.AttachmentRender;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StandbySlotBuffer {
    private static List<Object[]> pending = Collections.emptyList();

    private StandbySlotBuffer() {
    }

    public static void setPending(List<Object[]> standby) {
        pending = standby;
    }

    public static List<Object[]> takePending() {
        List<Object[]> list = pending;
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
            com.ssscript.taczfixes.client.mixin.MixinBedrockAttachmentModelScopeSuppress acc =
                    (com.ssscript.taczfixes.client.mixin.MixinBedrockAttachmentModelScopeSuppress) model;
            List<List<BedrockPart>> ocular = acc.taczfixes$ocularNodePaths();
            List<Boolean> isScopeOcular = acc.taczfixes$isScopeOcular();
            if (ocular == null) return;
            for (int i = 0; i < ocular.size(); i++) {
                boolean scopeType = isScopeOcular != null && i < isScopeOcular.size() && isScopeOcular.get(i);
                List<BedrockPart> path = ocular.get(i);
                if (path == null || path.isEmpty()) continue;
                boolean wantVisible = scopeType;
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

    /** 按记录的原值恢复可见性(同一部件多次改动也只恢复一次)。 */
    public static void restoreOcularVisibility(java.util.Map<BedrockPart, Boolean> restore) {
        for (java.util.Map.Entry<BedrockPart, Boolean> entry : restore.entrySet()) {
            entry.getKey().visible = entry.getValue();
        }
        restore.clear();
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
                adapterModel.render(ItemStack.EMPTY, gun, poseStack,
                        ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        RenderType.entityCutout(adapterTexture), light, overlay);
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
