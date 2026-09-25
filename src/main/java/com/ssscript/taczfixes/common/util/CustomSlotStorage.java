package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.VirtualOemAttachment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class CustomSlotStorage {
    public static final String TAG_KEY = "TaczFixesCustomSlots";
    public static final String ADAPTER_TAG_KEY = "TaczFixesCustomSlotAdapters";
    /** 原厂件选择: slotId -> attachmentId(空字符串表示已被显式卸下, 默认原厂件不再生效)。 */
    public static final String BUILTIN_TAG_KEY = "TaczFixesCustomBuiltinSlots";

    private CustomSlotStorage() {
    }

    /** 仅返回实际安装在槽位 NBT 中的物理配件。 */
    public static ItemStack getPhysical(ItemStack gun, String slotId) {
        CompoundTag tag = gun.getTag();
        if (tag == null || !tag.contains(TAG_KEY, 10)) return ItemStack.EMPTY;
        CompoundTag slots = tag.getCompound(TAG_KEY);
        if (!slots.contains(slotId, 10)) return ItemStack.EMPTY;
        return ItemStack.of(slots.getCompound(slotId));
    }

    /** 返回槽位当前实际生效的配件: 物理配件优先, 否则为原厂件(显式选择或 default_attached 虚拟件)。 */
    public static ItemStack get(ItemStack gun, String slotId) {
        ItemStack physical = getPhysical(gun, slotId);
        if (!physical.isEmpty()) return physical;
        return CustomSlotManager.buildBuiltinItem(resolveBuiltinId(gun, slotId));
    }

    /** 当前生效的原厂件 id: 显式选择 > default_attached; 已显式清空或未配置返回 null。 */
    public static ResourceLocation resolveBuiltinId(ItemStack gun, String slotId) {
        if (gun == null || gun.isEmpty()) return null;
        String raw = rawBuiltinEntry(gun, slotId);
        if (raw != null) {
            return raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
        }
        return CustomSlotManager.getBuiltinDefault(defOf(gun, slotId));
    }

    public static ResourceLocation getAdapter(ItemStack gun, String slotId) {
        CompoundTag tag = gun.getTag();
        if (tag == null || !tag.contains(ADAPTER_TAG_KEY, 10)) return null;
        CompoundTag adapters = tag.getCompound(ADAPTER_TAG_KEY);
        if (!adapters.contains(slotId, 8)) return null;
        return ResourceLocation.tryParse(adapters.getString(slotId));
    }

    public static void setAdapter(ItemStack gun, String slotId, ResourceLocation adapterId) {
        if (gun == null || gun.isEmpty()) return;
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag adapters = tag.contains(ADAPTER_TAG_KEY, 10) ? tag.getCompound(ADAPTER_TAG_KEY) : new CompoundTag();
        if (adapterId == null) {
            adapters.remove(slotId);
        } else {
            adapters.putString(slotId, adapterId.toString());
        }
        tag.put(ADAPTER_TAG_KEY, adapters);
    }

    public static ResourceLocation getAttachmentId(ItemStack gun, String slotId) {
        ItemStack item = get(gun, slotId);
        if (item.isEmpty()) return null;
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        return attachment == null ? null : attachment.getAttachmentId(item);
    }

    /** 安装物理配件; 同时清空该槽的原厂件选择, 避免卸下后默认原厂件"复活"。 */
    public static void install(ItemStack gun, String slotId, ItemStack item) {
        if (item == null || item.isEmpty()) return;
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag slots = tag.contains(TAG_KEY, 10) ? tag.getCompound(TAG_KEY) : new CompoundTag();
        slots.put(slotId, item.save(new CompoundTag()));
        tag.put(TAG_KEY, slots);
        setBuiltinEntry(gun, slotId, "");
    }

    /** 选择原厂件(虚拟 OEM): 清空物理配件, 卸下不会返还到背包。 */
    public static void installBuiltin(ItemStack gun, String slotId, ResourceLocation attachmentId) {
        if (gun == null || gun.isEmpty() || attachmentId == null) return;
        removePhysical(gun, slotId);
        setBuiltinEntry(gun, slotId, attachmentId.toString());
    }

    /**
     * 卸下槽位配件:
     * 物理件返还(原厂件/候选件除外, 直接消失); 虚拟原厂件直接消失并记录为已卸下。
     */
    public static ItemStack unload(ItemStack gun, String slotId) {
        ItemStack old = getPhysical(gun, slotId);
        if (!old.isEmpty()) {
            removePhysical(gun, slotId);
            setBuiltinEntry(gun, slotId, "");
            IAttachment attachment = IAttachment.getIAttachmentOrNull(old);
            ResourceLocation id = attachment == null ? null : attachment.getAttachmentId(old);
            CustomSlotDefinition def = defOf(gun, slotId);
            if (VirtualOemAttachment.isMarked(old)
                    || (id != null && CustomSlotManager.isBuiltinCandidate(def, id))) {
                return ItemStack.EMPTY;
            }
            return old;
        }
        if (resolveBuiltinId(gun, slotId) != null) {
            setBuiltinEntry(gun, slotId, "");
        }
        return ItemStack.EMPTY;
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

    private static CustomSlotDefinition defOf(ItemStack gun, String slotId) {
        IGun igun = IGun.getIGunOrNull(gun);
        return igun == null ? null : CustomSlotManager.getSlot(igun.getGunId(gun), slotId);
    }

    private static String rawBuiltinEntry(ItemStack gun, String slotId) {
        CompoundTag tag = gun.getTag();
        if (tag == null || !tag.contains(BUILTIN_TAG_KEY, 10)) return null;
        CompoundTag builtin = tag.getCompound(BUILTIN_TAG_KEY);
        if (!builtin.contains(slotId, 8)) return null;
        return builtin.getString(slotId);
    }

    private static void setBuiltinEntry(ItemStack gun, String slotId, String value) {
        if (gun == null || gun.isEmpty()) return;
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag builtin = tag.contains(BUILTIN_TAG_KEY, 10) ? tag.getCompound(BUILTIN_TAG_KEY) : new CompoundTag();
        builtin.putString(slotId, value == null ? "" : value);
        tag.put(BUILTIN_TAG_KEY, builtin);
    }

    private static void removePhysical(ItemStack gun, String slotId) {
        CompoundTag tag = gun.getTag();
        if (tag != null && tag.contains(TAG_KEY, 10)) {
            tag.getCompound(TAG_KEY).remove(slotId);
        }
    }
}
