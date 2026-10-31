package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.DurabilityStorage;
import com.ssscript.taczfixes.common.util.ItemNbt;
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
import moze_intel.projecte.api.components.DataComponentProcessor;
import moze_intel.projecte.api.components.IDataComponentProcessor;
import moze_intel.projecte.emc.EMCMappingHandler;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;

@DataComponentProcessor(priority = 1000, requiredMods = {"projecte", "tacz"})
public class GunNbtEMCProcessor implements IDataComponentProcessor {
    @Override
    public String getTranslationKey() {
        return "projecte.gun_nbt_processor.taczfixes.gun";
    }


    @Override
    public String getName() {
        return "TaczGun";
    }

    @Override
    public String getDescription() {
        return "TaCZ gun EMC includes loaded ammo, installed attachments and durability loss; preserves TaCZ data in ProjectE.";
    }

    @Override
    public boolean hasPersistentComponents() {
        return true;
    }

    private static CompoundTag persistentTag(ItemInfo info) {
        CompoundTag nbt = info.getOrNull(DataComponents.CUSTOM_DATA) == null ? null
                : info.getOrNull(DataComponents.CUSTOM_DATA).copyTag();
        ItemStack stack = info.createStack();
        if (nbt != null) {
            ItemNbt.set(stack, nbt);
        }
        try {
            if (stack.getItem() instanceof IAmmoBox ammoBox) {
                CompoundTag result = new CompoundTag();
                result.putInt(AmmoBoxItemDataAccessor.LEVEL_TAG, ammoBox.getAmmoLevel(stack));
                if (ammoBox.isAllTypeCreative(stack)) {
                    result.putBoolean(AmmoBoxItemDataAccessor.ALL_TYPE_CREATIVE_TAG, true);
                } else if (ammoBox.isCreative(stack)) {
                    result.putBoolean(AmmoBoxItemDataAccessor.CREATIVE_TAG, true);
                }
                return result;
            }
            if (stack.getItem() instanceof IGun gun) {
                ResourceLocation id = gun.getGunId(stack);
                if (id == null) return null;
                CompoundTag result = new CompoundTag();
                result.putString(GunItemDataAccessor.GUN_ID_TAG, id.toString());
                return result;
            }
            if (stack.getItem() instanceof IAttachment attachment) {
                ResourceLocation id = attachment.getAttachmentId(stack);
                if (id == null) return null;
                CompoundTag result = new CompoundTag();
                result.putString(AttachmentItemDataAccessor.ATTACHMENT_ID_TAG, id.toString());
                return result;
            }
            if (stack.getItem() instanceof IAmmo ammo) {
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
    public void collectPersistentComponents(@NotNull ItemInfo info, DataComponentPatch.Builder builder) {
        CompoundTag tag = persistentTag(info);
        if (tag != null) {
            builder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public long recalculateEMC(@NotNull ItemInfo info, long currentEMC) throws ArithmeticException {
        ItemStack stack = info.createStack();
        CompoundTag nbt = info.getOrNull(DataComponents.CUSTOM_DATA) == null ? null
                : info.getOrNull(DataComponents.CUSTOM_DATA).copyTag();
        if (stack.getItem() instanceof IAmmoBox ammoBox) {
            return ammoBoxEmc(ammoBox, stack);
        }
        if (!(stack.getItem() instanceof IGun gun) || nbt == null) {
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
                CompoundTag attachmentTag = gun.getAttachmentTag(stack, type);
                ResourceLocation installedId = AttachmentItemDataAccessor.getAttachmentIdFromTag(attachmentTag);
                if (installedId == null) {
                    continue;
                }
                ResourceLocation builtinId = gun.getBuiltInAttachmentId(stack, type);
                if (installedId.equals(builtinId)) {
                    continue;
                }
                long value = storedAttachment(installedId);
                if (value > 0) {
                    total = add(total, value);
                }
            }
            var durabilityConfig = DurabilityStorage.config(stack);
            int maxDurability = DurabilityStorage.getMax(stack);
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
        long total = stored(ModItems.AMMO_BOX.get(), baseTag);
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
        long ammoEmc = stored(ModItems.AMMO.get(), ammoTag);
        if (ammoEmc <= 0L) {
            return total;
        }
        return add(total, multiply(ammoEmc, count));
    }

    private static long storedGun(ResourceLocation gunId) {
        CompoundTag tag = new CompoundTag();
        tag.putString(GunItemDataAccessor.GUN_ID_TAG, gunId.toString());
        return stored(ModItems.MODERN_KINETIC_GUN.get(), tag);
    }

    private static long storedAttachment(ResourceLocation id) {
        CompoundTag tag = new CompoundTag();
        tag.putString(AttachmentItemDataAccessor.ATTACHMENT_ID_TAG, id.toString());
        return stored(ModItems.ATTACHMENT.get(), tag);
    }

    private static long stored(Item item, CompoundTag tag) {
        ItemStack stack = new ItemStack(item);
        ItemNbt.set(stack, tag);
        return EMCMappingHandler.getStoredEmcValue(ItemInfo.fromStack(stack));
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
            long ammoEmc = stored(ModItems.AMMO.get(), tag);
            if (ammoEmc <= 0) {
                return 0L;
            }
            if (gunData.getReloadData().getType() == FeedType.FUEL) {
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
