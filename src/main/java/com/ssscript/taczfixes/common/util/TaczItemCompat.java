package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

public final class TaczItemCompat {
    private TaczItemCompat() {
    }

    public static ItemStack getAttachment(IGun gun, ItemStack gunStack, AttachmentType type) {
        HolderLookup.Provider provider = RegistryAccessHelper.provider();
        if (gun == null || gunStack == null || provider == null) {
            return ItemStack.EMPTY;
        }
        return gun.getAttachment(provider, gunStack, type);
    }

    public static void installAttachment(IGun gun, ItemStack gunStack, ItemStack attachment) {
        HolderLookup.Provider provider = RegistryAccessHelper.provider();
        if (gun != null && gunStack != null && provider != null) {
            gun.installAttachment(provider, gunStack, attachment);
        }
    }

    public static void unloadAttachment(IGun gun, ItemStack gunStack, AttachmentType type) {
        HolderLookup.Provider provider = RegistryAccessHelper.provider();
        if (gun != null && gunStack != null && provider != null) {
            gun.unloadAttachment(provider, gunStack, type);
        }
    }
}
