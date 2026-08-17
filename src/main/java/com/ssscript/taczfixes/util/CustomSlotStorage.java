package com.ssscript.taczfixes.util;

import com.ssscript.taczfixes.data.CustomSlotDefinition;
import com.ssscript.taczfixes.data.CustomSlotManager;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class CustomSlotStorage {
    public static final String TAG_KEY = "TaczFixesCustomSlots";

    private CustomSlotStorage() {
    }

    public static ItemStack get(ItemStack gun, String slotId) {
        CompoundTag tag = gun.getTag();
        if (tag == null || !tag.contains(TAG_KEY, 10)) return ItemStack.EMPTY;
        CompoundTag slots = tag.getCompound(TAG_KEY);
        if (!slots.contains(slotId, 10)) return ItemStack.EMPTY;
        return ItemStack.of(slots.getCompound(slotId));
    }

    public static ResourceLocation getAttachmentId(ItemStack gun, String slotId) {
        ItemStack item = get(gun, slotId);
        if (item.isEmpty()) return null;
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        return attachment == null ? null : attachment.getAttachmentId(item);
    }

    public static void install(ItemStack gun, String slotId, ItemStack item) {
        if (item == null || item.isEmpty()) return;
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag slots = tag.contains(TAG_KEY, 10) ? tag.getCompound(TAG_KEY) : new CompoundTag();
        slots.put(slotId, item.save(new CompoundTag()));
        tag.put(TAG_KEY, slots);
    }

    public static ItemStack unload(ItemStack gun, String slotId) {
        ItemStack old = get(gun, slotId);
        if (old.isEmpty()) return ItemStack.EMPTY;
        CompoundTag tag = gun.getTag();
        if (tag != null && tag.contains(TAG_KEY, 10)) {
            tag.getCompound(TAG_KEY).remove(slotId);
        }
        return old;
    }

    public static ItemStack getLaserLike(ItemStack gun) {
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) return ItemStack.EMPTY;
        ResourceLocation gunId = igun.getGunId(gun);
        for (Map.Entry<String, CustomSlotDefinition> entry : CustomSlotManager.getSlots(gunId).entrySet()) {
            CustomSlotDefinition def = entry.getValue();
            if (def == null || def.isCustom()) continue;
            try {
                AttachmentType defType = AttachmentType.valueOf(def.type.toUpperCase());
                if (defType != AttachmentType.LASER) continue;
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ItemStack item = get(gun, entry.getKey());
            if (!item.isEmpty()) return item;
        }
        return ItemStack.EMPTY;
    }

    public static boolean hasStandardLaser(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        if (tag == null) return false;
        String key = "Attachment" + AttachmentType.LASER.name();
        return tag.contains(key, 10) && !ItemStack.of(tag.getCompound(key)).isEmpty();
    }

    public static boolean isLaserFromCustomSlot(ItemStack gun, ItemStack laserItem) {
        if (laserItem == null || laserItem.isEmpty()) return false;
        if (hasStandardLaser(gun)) return false;
        ItemStack custom = getLaserLike(gun);
        if (custom.isEmpty()) return false;
        ResourceLocation a = laserItem.getItem() instanceof IAttachment
                ? ((IAttachment) laserItem.getItem()).getAttachmentId(laserItem) : null;
        ResourceLocation b = custom.getItem() instanceof IAttachment
                ? ((IAttachment) custom.getItem()).getAttachmentId(custom) : null;
        return a != null && a.equals(b);
    }
}
