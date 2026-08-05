package com.example.taczfixes.util;

import com.example.taczfixes.Config;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class BurstBlockHelper {
    private static final ResourceLocation EMPTY = DefaultAssets.EMPTY_ATTACHMENT_ID;

    public static boolean hasRestrictedAttachment(IGun iGun, ItemStack gunItem) {
        List<? extends String> restrictedIds = Config.BURST_BLOCK_ATTACHMENTS.get();
        if (restrictedIds.isEmpty()) return false;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation attachmentId = iGun.getAttachmentId(gunItem, type);
            if (!EMPTY.equals(attachmentId) && restrictedIds.contains(attachmentId.toString())) {
                return true;
            }
            ResourceLocation builtInId = iGun.getBuiltInAttachmentId(gunItem, type);
            if (!EMPTY.equals(builtInId) && restrictedIds.contains(builtInId.toString())) {
                return true;
            }
        }
        return false;
    }
}
