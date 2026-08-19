package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.AttachmentRender;
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
                                            int light, int overlay) {
        poseStack.pushPose();
        applyNodePathTransform(node, poseStack);
        applyPosAlter(node, gun, poseStack);
        AttachmentRender.renderAttachment(item, gun, poseStack, displayContext, light, overlay);
        poseStack.popPose();
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