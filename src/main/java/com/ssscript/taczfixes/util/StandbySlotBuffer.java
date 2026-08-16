package com.ssscript.taczfixes.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.AttachmentRender;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
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
        ItemDisplayContext ctx = displayContext;
        BedrockAttachmentModel model = attachmentModelOf(item);
        if (model != null && model.isScope() && displayContext.firstPerson()) {
            ctx = ItemDisplayContext.NONE;
        }
        AttachmentRender.renderAttachment(item, gun, poseStack, ctx, light, overlay);
        poseStack.popPose();
    }

    private static BedrockAttachmentModel attachmentModelOf(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return null;
        return TimelessAPI.getClientAttachmentIndex(ia.getAttachmentId(item))
                .map(ClientAttachmentIndex::getAttachmentModel).orElse(null);
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