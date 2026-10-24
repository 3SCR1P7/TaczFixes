package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.DurabilityStorage;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AmmoBoxItemDataAccessor;
import com.tacz.guns.api.item.nbt.AmmoItemDataAccessor;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.nbt.INBTProcessor;
import moze_intel.projecte.api.nbt.NBTProcessor;
import moze_intel.projecte.emc.EMCMappingHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 背包内枪械的 EMC = 枪械本体 + 已装填弹药 + 已安装配件, 再按剩余耐久折算。
 * 除 GunId、弹药数和配件/耐久外, 其它 NBT 不影响结果。
 * 同时保留 TaCZ 物品的 NBT, 避免转化桌输出无 NBT 的紫黑方块。
 */
@NBTProcessor(priority = 1000, requiredMods = {"projecte", "tacz"})
public class GunNbtEMCProcessor implements INBTProcessor {

    @Override
    public String getName() {
        return "TaczGun";
    }

    @Override
    public String getDescription() {
        return "TaCZ gun EMC includes loaded ammo, installed attachments and durability loss; preserves TaCZ NBT in ProjectE.";
    }

    @Override
    public boolean hasPersistentNBT() {
        return true;
    }

    @Override
    public CompoundTag getPersistentNBT(ItemInfo info) {
        CompoundTag nbt = info.getNBT();
        if (nbt == null) {
            return null;
        }
        ItemStack stack = new ItemStack(info.getItem());
        stack.setTag(nbt.copy());
        try {
            if (info.getItem() instanceof IAmmoBox ammoBox) {
                CompoundTag result = new CompoundTag();
                result.putInt(AmmoBoxItemDataAccessor.LEVEL_TAG, ammoBox.getAmmoLevel(stack));
                if (ammoBox.isAllTypeCreative(stack)) {
                    result.putBoolean(AmmoBoxItemDataAccessor.ALL_TYPE_CREATIVE_TAG, true);
                } else if (ammoBox.isCreative(stack)) {
                    result.putBoolean(AmmoBoxItemDataAccessor.CREATIVE_TAG, true);
                }
                return result;
            }
            if (info.getItem() instanceof IGun gun) {
                ResourceLocation id = gun.getGunId(stack);
                if (id == null) return null;
                CompoundTag result = new CompoundTag();
                result.putString(GunItemDataAccessor.GUN_ID_TAG, id.toString());
                return result;
            }
            if (info.getItem() instanceof IAttachment attachment) {
                ResourceLocation id = attachment.getAttachmentId(stack);
                if (id == null) return null;
                CompoundTag result = new CompoundTag();
                result.putString(AttachmentItemDataAccessor.ATTACHMENT_ID_TAG, id.toString());
                return result;
            }
            if (info.getItem() instanceof IAmmo ammo) {
                ResourceLocation id = ammo.getAmmoId(stack);
                if (id == null) return null;
                CompoundTag result = new CompoundTag();
                result.putString(AmmoItemDataAccessor.AMMO_ID_TAG, id.toString());
                return result;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    @Override
    public long recalculateEMC(ItemInfo info, long currentEMC) {
        Item item = info.getItem();
        CompoundTag nbt = info.getNBT();
        ItemStack stack = new ItemStack(item);
        if (nbt != null) {
            stack.setTag(nbt.copy());
        }
        if (item instanceof IAmmoBox ammoBox) {
            return ammoBoxEmc(ammoBox, stack);
        }
        if (!(item instanceof IGun gun) || nbt == null) {
            return currentEMC;
        }
        ResourceLocation gunId = gun.getGunId(stack);
        if (gunId == null) {
            return 0;
        }
        long base = storedGun(gunId);
        if (base <= 0) {
            return 0;
        }
        long total = base;
        try {
            long ammoValue = ammoInfo(gun, stack, gunId);
            if (ammoValue > 0) {
                total = add(total, ammoValue);
            }
            for (AttachmentType type : AttachmentType.values()) {
                if (type == AttachmentType.NONE) {
                    continue;
                }
                ItemStack attachment = gun.getAttachment(stack, type);
                if (attachment.isEmpty()) {
                    continue;
                }
                IAttachment attachmentData = IAttachment.getIAttachmentOrNull(attachment);
                if (attachmentData == null) {
                    continue;
                }
                ResourceLocation installedId = attachmentData.getAttachmentId(attachment);
                ResourceLocation builtinId = gun.getBuiltInAttachmentId(stack, type);
                if (installedId != null && installedId.equals(builtinId)) {
                    // 原厂/内置配件已包含在枪械本体 EMC 中, 不重复计算。
                    continue;
                }
                long value = storedAttachment(attachment);
                if (value > 0) {
                    total = add(total, value);
                }
            }
            var durabilityConfig = DurabilityStorage.config(stack);
            int maxDurability = DurabilityStorage.getMax(stack);
            // 只有 damage_action=remove 的耐久机制才影响 EMC; disable/none 不折算。
            if (maxDurability > 0 && "remove".equalsIgnoreCase(
                    DurabilityStorage.damageAction(durabilityConfig))) {
                int current = DurabilityStorage.get(stack);
                total = total * Math.max(0, current) / maxDurability;
            }
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, total);
    }

    private static long ammoBoxEmc(IAmmoBox ammoBox, ItemStack stack) {
        if (ammoBox.isCreative(stack) || ammoBox.isAllTypeCreative(stack)) {
            return 0L;
        }
        CompoundTag baseTag = new CompoundTag();
        baseTag.putInt(AmmoBoxItemDataAccessor.LEVEL_TAG, ammoBox.getAmmoLevel(stack));
        long total = EMCMappingHandler.getStoredEmcValue(
                ItemInfo.fromItem(ModItems.AMMO_BOX.get(), baseTag));
        if (total <= 0L) {
            return 0L;
        }
        ResourceLocation ammoId = ammoBox.getAmmoId(stack);
        int count = ammoBox.getAmmoCount(stack);
        if (ammoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId) || count <= 0) {
            return total;
        }
        CompoundTag ammoTag = new CompoundTag();
        ammoTag.putString(AmmoItemDataAccessor.AMMO_ID_TAG, ammoId.toString());
        long ammoEmc = EMCMappingHandler.getStoredEmcValue(
                ItemInfo.fromItem(ModItems.AMMO.get(), ammoTag));
        if (ammoEmc <= 0L) {
            return total;
        }
        try {
            return Math.addExact(total, Math.multiplyExact(ammoEmc, (long) count));
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static long storedGun(ResourceLocation gunId) {
        CompoundTag tag = new CompoundTag();
        tag.putString(GunItemDataAccessor.GUN_ID_TAG, gunId.toString());
        return EMCMappingHandler.getStoredEmcValue(
                ItemInfo.fromItem(ModItems.MODERN_KINETIC_GUN.get(), tag));
    }

    private static long storedAttachment(ItemStack attachment) {
        IAttachment data = IAttachment.getIAttachmentOrNull(attachment);
        if (data == null) {
            return 0;
        }
        ResourceLocation id = data.getAttachmentId(attachment);
        if (id == null) {
            return 0;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(AttachmentItemDataAccessor.ATTACHMENT_ID_TAG, id.toString());
        long value = EMCMappingHandler.getStoredEmcValue(
                ItemInfo.fromItem(ModItems.ATTACHMENT.get(), tag));
        if (value > 0) {
            return value;
        }
        return EMCMappingHandler.getStoredEmcValue(ItemInfo.fromStack(attachment));
    }

    private static long ammoInfo(IGun gun, ItemStack stack, ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId).map(index -> {
            GunData gunData = index.getGunData();
            ResourceLocation ammoId = gunData.getAmmoId();
            if (ammoId == null || Config.AMMO_EMC_BLACKLIST.get().contains(ammoId.toString())) {
                return 0L;
            }
            int current = Math.max(0, gun.getCurrentAmmoCount(stack));
            CompoundTag tag = new CompoundTag();
            tag.putString(AmmoItemDataAccessor.AMMO_ID_TAG, ammoId.toString());
            long ammoEmc = EMCMappingHandler.getStoredEmcValue(
                    ItemInfo.fromItem(ModItems.AMMO.get(), tag));
            if (ammoEmc <= 0) {
                return 0L;
            }
            if (gunData.getReloadData().getType() == FeedType.FUEL) {
                // 一颗燃料弹即可装满弹匣: 满弹匣只算一颗, 不满按比例折算。
                int capacity = Math.max(0, AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData));
                if (capacity <= 0 || current <= 0) {
                    return 0L;
                }
                return multiply(ammoEmc, Math.min(current, capacity)) / capacity;
            }
            int count = current + (gun.hasBulletInBarrel(stack) ? 1 : 0);
            if (count <= 0) {
                return 0L;
            }
            return multiply(ammoEmc, count);
        }).orElse(0L);
    }

    private static long add(long a, long b) {
        return Math.addExact(a, b);
    }

    private static long multiply(long a, long b) {
        return Math.multiplyExact(a, b);
    }
}
