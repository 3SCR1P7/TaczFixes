package com.ssscript.taczfixes.common.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.data.AttachmentGroupOffsetManager;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;

public final class AttachmentGroupOffsetHelper {
    private AttachmentGroupOffsetHelper() {
    }

    public static void apply(PoseStack pose, EnumMap<AttachmentType, ItemStack> attachments, String slotTypeName) {
        float[] sum = sumOffsets(attachments, slotTypeName);
        if (sum != null) {
            pose.translate(sum[0] / 16.0F, sum[1] / 16.0F, sum[2] / 16.0F);
        }
    }

    /** 直接从枪械物品汇总该槽位的 group_offset (静态渲染路径拿不到 BedrockGunModel)。 */
    public static void applyForGun(PoseStack pose, ItemStack gunStack, String slotTypeName) {
        if (gunStack == null || gunStack.isEmpty()) return;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        boolean any = false;
        for (AttachmentType slot : AttachmentType.values()) {
            if (slot == AttachmentType.NONE) continue;
            ItemStack stack = gun.getAttachment(gunStack, slot);
            if (stack == null || stack.isEmpty()) {
                stack = gun.getBuiltinAttachment(gunStack, slot);
            }
            if (stack == null || stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof IAttachment attachment)) continue;
            float[] offset = AttachmentGroupOffsetManager.getOffset(attachment.getAttachmentId(stack), slotTypeName);
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

    /** 汇总所有已安装配件在该槽位(slotTypeName)的 group_offset, 返回 16 单位 {x, y, z}; 无任何偏移返回 null。 */
    public static float[] sumOffsets(EnumMap<AttachmentType, ItemStack> attachments, String slotTypeName) {
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        boolean any = false;
        if (attachments != null) {
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
        }
        return any ? new float[]{x, y, z} : null;
    }
}