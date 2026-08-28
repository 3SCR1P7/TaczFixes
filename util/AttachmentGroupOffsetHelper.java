package com.ssscript.taczfixes.common.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.data.AttachmentGroupOffsetManager;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;

public final class AttachmentGroupOffsetHelper {
    private AttachmentGroupOffsetHelper() {
    }

    public static void apply(PoseStack pose, EnumMap<AttachmentType, ItemStack> attachments, String slotTypeName) {
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        boolean any = false;
        for (AttachmentType slot : AttachmentType.values()) {
            ItemStack stack = attachments.get(slot);
            if (stack == null || stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (!(item instanceof IAttachment attachment)) continue;
            ResourceLocation id = attachment.getAttachmentId(stack);
            float[] offset = AttachmentGroupOffsetManager.getOffset(id, slotTypeName);
            if (offset == null) continue;
            x += offset[0];
            y += offset[1];
            z += offset[2];
            any = true;
        }
        if (any) {
            pose.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        }
    }
}