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

    /**
     * 渲染非原生槽位的瞄具(玩家未使用时): 按普通配件(如 laser)管线渲染 —— AttachmentRender.renderAttachment
     * 处理模型/UV/贴图, 传入 THIRD_PERSON 避免触发 TACZ 第一人称 renderScope/renderSight/renderBoth 的
     * ocular 功能路径与模板剔除。不手动设置 ocular 可见性。
     */
    public static void renderRawMesh(ItemStack item, ItemStack gun, BedrockPart node,
                                     PoseStack poseStack, ItemDisplayContext displayContext,
                                     int light, int overlay, String slotId) {
        poseStack.pushPose();
        applyNodePathTransform(node, poseStack);
        applyPosAlter(node, gun, poseStack);
        poseStack.translate(0.0F, -1.5F, 0.0F);
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
        // 与普通配件一致: 用 AttachmentRender 渲染; 强制 THIRD_PERSON 避免 ocular 功能路径/模板
        AttachmentRender.renderAttachment(item, gun, type, poseStack,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, light, overlay);
        poseStack.popPose();
    }

    /** 隐藏该瞄具的 sight 型 ocular(ocular_sight/红点镜片), 组合镜的 sight 部分一并隐藏。 */
    public static void hideOcularSightParts(BedrockAttachmentModel model,
                                            List<BedrockPart> restore) {
        try {
            com.ssscript.taczfixes.client.mixin.MixinBedrockAttachmentModelScopeSuppress acc =
                    (com.ssscript.taczfixes.client.mixin.MixinBedrockAttachmentModelScopeSuppress) model;
            List<List<BedrockPart>> ocular = acc.taczfixes$ocularNodePaths();
            List<Boolean> isScopeOcular = acc.taczfixes$isScopeOcular();
            if (ocular == null) return;
            for (int i = 0; i < ocular.size(); i++) {
                // sight 型(ocular_sight)隐藏; scope 型(ocular_scope)保持可见
                if (isScopeOcular != null && i < isScopeOcular.size() && isScopeOcular.get(i)) {
                    continue;
                }
                List<BedrockPart> path = ocular.get(i);
                if (path == null || path.isEmpty()) continue;
                for (BedrockPart p : path) {
                    if (p.visible) {
                        p.visible = false;
                        restore.add(p);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** standby(未使用)渲染前: 保证 scope 型 ocular(含其祖先链)可见、sight 型 ocular 隐藏。
     *  renderTempPart/树遍历可能把父节点设为 false, 导致子树(ocular_scope)不渲染, 故连同祖先一起恢复可见。 */
    public static void ensureScopeOcularVisible(BedrockAttachmentModel model,
                                                List<BedrockPart> restore) {
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
                        p.visible = wantVisible;
                        restore.add(p);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void restoreOcularVisibility(List<BedrockPart> restore) {
        for (BedrockPart part : restore) {
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
