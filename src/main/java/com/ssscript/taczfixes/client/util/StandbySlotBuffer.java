package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.AttachmentRender;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;

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
        poseStack.pushPose();
        applyNodePathTransform(node, poseStack);
        applyPosAlter(node, gun, poseStack);
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

    public static void renderRawMesh(ItemStack item, ItemStack gun, BedrockPart node,
                                     PoseStack poseStack, ItemDisplayContext displayContext,
                                     int light, int overlay, String slotId) {
        poseStack.pushPose();
        applyNodePathTransform(node, poseStack);
        applyPosAlter(node, gun, poseStack);
        poseStack.translate(0.0F, -1.5F, 0.0F);
        applySlotAdapterOffset(item, gun, slotId, poseStack, displayContext, light, overlay);
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia != null) {
            ResourceLocation id = ia.getAttachmentId(item);
            TimelessAPI.getClientAttachmentIndex(id).ifPresent(index -> {
                BedrockAttachmentModel model = index.getAttachmentModel();
                ResourceLocation texture = index.getModelTexture();
                if (model != null && texture != null) {
                    // 完整渲染(设置模型上下文, 部件可见性/动画正确), 但伪装为第三人称:
                    // 9-param 分支走非第一人称路径(renderTempPart + super.render),
                    // 完全不触发 renderScope/renderSight/clearStencil 的模板逻辑。
                    // ocular 节点以普通 cube 身份参与树遍历: 渲染前按类型设置可见性,
                    // 筒状(scope 型)可见、红点(sight 型)不可见, 渲染后恢复原状。
                    RenderType renderType = RenderType.entityCutout(texture);
                    java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> restore = new java.util.ArrayList<>();
                    ensureOcularVisibility(model, restore);
                    try {
                        model.render(item, gun, poseStack,
                                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                                renderType, light, overlay);
                    } finally {
                        restoreOcularVisibility(restore);
                    }
                }
            });
        }
        poseStack.popPose();
    }

   /**
     * 渲染前调用: 将 ocular 节点设为"普通 cube"可见性 ——
     * 筒状(scope 型)ocular 置为可见, 红点/全息(sight 型)ocular 置为不可见,
     * 使非开镜渲染时 ocular 以常规树遍历显示(而非手动 renderTempPart 补绘)。
     * 通过 restoreOcularVisibility 恢复原值。
     */
    public static void ensureOcularVisibility(BedrockAttachmentModel model,
                                              java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> restore) {
        try {
            com.ssscript.taczfixes.client.mixin.MixinBedrockAttachmentModelScopeSuppress acc =
                    (com.ssscript.taczfixes.client.mixin.MixinBedrockAttachmentModelScopeSuppress) model;
            java.util.List<java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart>> ocular = acc.taczfixes$ocularNodePaths();
            java.util.List<Boolean> isScopeOcular = acc.taczfixes$isScopeOcular();
            if (ocular != null) {
                for (int i = 0; i < ocular.size(); i++) {
                    java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> path = ocular.get(i);
                    if (path == null || path.isEmpty()) continue;
                    java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> all = new java.util.ArrayList<>(path);
                    boolean wantVisible = isScopeOcular == null || i >= isScopeOcular.size() || isScopeOcular.get(i);
                    for (com.tacz.guns.client.model.bedrock.BedrockPart p : all) {
                        if (p.visible != wantVisible) {
                            p.visible = wantVisible;
                            restore.add(p);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void restoreOcularVisibility(java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> restore) {
        for (com.tacz.guns.client.model.bedrock.BedrockPart part : restore) {
            part.visible = !part.visible;
        }
        restore.clear();
    }

    /**
     * 按槽适配器: 渲染适配器模型(第三人称伪装)并应用 mountOffset(x/16, -y/16, z/16),
     * 与瞄准视角偏移(handleScopeViewShift)方向一致。仅在适配器存在且允许当前配件时生效。
     */
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

    private static void applyPosAlter(BedrockPart node, ItemStack gun, PoseStack pose) {
        if (node == null || node.name == null || gun == null || gun.isEmpty()) return;
        String name = node.name;
        if (!name.endsWith("_pos")) return;
        String slotKey = name.substring(0, name.length() - "_pos".length());
        float z = PosAlterStorage.get(gun, slotKey);
        if (z != 0.0F) {
            pose.translate(0.0F, 0.0F, z / 16.0F);
        }
    }

    public static void applyNodePathTransform(BedrockPart node, PoseStack pose) {
        java.util.List<BedrockPart> path = new java.util.ArrayList<>();
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
