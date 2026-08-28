package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class AmmoReplaceHelper {
    private AmmoReplaceHelper() {
    }

    public static ResourceLocation resolveAmmoId(ItemStack gunStack, ResourceLocation baseAmmoId) {
        if (gunStack == null || gunStack.isEmpty() || baseAmmoId == null) {
            return null;
        }
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            return null;
        }
        ResourceLocation gunId = gun.getGunId(gunStack);
        if (gunId == null) {
            return null;
        }
        Map<String, String> replace = TaczFixesDataManager.resolveAmmoReplace(gunId);
        if (replace.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> e : replace.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            boolean found = hasAttachment(gunStack, e.getKey());
            if (!found) {
                continue;
            }
            ResourceLocation replaced = ResourceLocation.tryParse(e.getValue());
            if (replaced != null) {
                return replaced;
            }
        }
        return null;
    }

    public static boolean checkAmmoOfGun(ItemStack gunStack, ItemStack ammoStack) {
        if (gunStack == null || gunStack.isEmpty() || ammoStack == null || ammoStack.isEmpty()) {
            return false;
        }
        Item gunItem = gunStack.getItem();
        if (!(gunItem instanceof IGun gun)) {
            return false;
        }
        Item aItem = ammoStack.getItem();
        if (!(aItem instanceof IAmmo ammo)) {
            return false;
        }
        ResourceLocation gunId = gun.getGunId(gunStack);
        ResourceLocation ammoId = ammo.getAmmoId(ammoStack);
        if (gunId == null || ammoId == null) {
            return false;
        }
        ResourceLocation base = TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getGunData().getAmmoId())
                .orElse(null);
        if (base == null) {
            return false;
        }
        ResourceLocation replaced = resolveAmmoId(gunStack, base);
        return replaced != null ? replaced.equals(ammoId) : base.equals(ammoId);
    }

    public static boolean checkAmmoBoxOfGun(ItemStack gunStack, ItemStack boxStack) {
        if (gunStack == null || gunStack.isEmpty() || boxStack == null || boxStack.isEmpty()) {
            return false;
        }
        Item gunItem = gunStack.getItem();
        if (!(gunItem instanceof IGun gun)) {
            return false;
        }
        Item bItem = boxStack.getItem();
        if (!(bItem instanceof IAmmoBox box)) {
            return false;
        }
        if (box.isAllTypeCreative(boxStack)) {
            return true;
        }
        ResourceLocation boxAmmoId = box.getAmmoId(boxStack);
        if (boxAmmoId == null || boxAmmoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
            return false;
        }
        ResourceLocation gunId = gun.getGunId(gunStack);
        if (gunId == null) {
            return false;
        }
        ResourceLocation base = TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getGunData().getAmmoId())
                .orElse(null);
        if (base == null) {
            return false;
        }
        ResourceLocation replaced = resolveAmmoId(gunStack, base);
        return replaced != null ? replaced.equals(boxAmmoId) : base.equals(boxAmmoId);
    }

    private static boolean hasAttachment(ItemStack gunStack, String id) {
        ResourceLocation target = ResourceLocation.tryParse(id);
        if (target == null) {
            return false;
        }
        CompoundTag tag = gunStack.getTag();
        if (tag == null) {
            return false;
        }
        String base = com.tacz.guns.api.item.nbt.GunItemDataAccessor.GUN_ATTACHMENT_BASE;
        String extKey = base + AttachmentType.EXTENDED_MAG.name();
        if (tag.contains(extKey, 10) && isTarget(tag.getCompound(extKey), target)) {
            return true;
        }
        return false;
    }

    private static boolean isTarget(CompoundTag wrapper, ResourceLocation target) {
        ItemStack installed = ItemStack.of(wrapper);
        if (installed.isEmpty()) {
            return false;
        }
        IAttachment ia = IAttachment.getIAttachmentOrNull(installed);
        return ia != null && target.equals(ia.getAttachmentId(installed));
    }
}