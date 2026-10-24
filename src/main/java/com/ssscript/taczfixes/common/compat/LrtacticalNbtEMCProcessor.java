package com.ssscript.taczfixes.common.compat;

import me.xjqsh.lrtactical.api.item.IConsumable;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.item.IThrowable;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.nbt.INBTProcessor;
import moze_intel.projecte.api.nbt.NBTProcessor;
import moze_intel.projecte.emc.EMCMappingHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * LRTactical's consumables, throwables and melee weapons use NBT only as a
 * subtype id. ProjectE would otherwise keep volatile tags such as durability,
 * display overrides and cooldown data in transmutation-table knowledge.
 */
@NBTProcessor(priority = 1000, requiredMods = {"projecte", "lrtactical"})
public class LrtacticalNbtEMCProcessor implements INBTProcessor {

    @Override
    public String getName() {
        return "LrtacticalIdNbtProcessor";
    }

    @Override
    public String getDescription() {
        return "Keeps only the LRTactical subtype id in ProjectE knowledge.";
    }

    @Override
    public boolean hasPersistentNBT() {
        return true;
    }

    @Override
    public CompoundTag getPersistentNBT(ItemInfo info) {
        ItemStack stack = info.createStack();
        Item item = stack.getItem();
        CompoundTag tag = new CompoundTag();
        if (item instanceof IConsumable consumable) {
            ResourceLocation id = consumable.getId(stack);
            if (id == null || IConsumable.EMPTY.equals(id)) {
                return null;
            }
            tag.putString(IConsumable.ID_TAG, id.toString());
        } else if (item instanceof IThrowable throwable) {
            ResourceLocation id = throwable.getId(stack);
            if (id == null || IThrowable.EMPTY.equals(id)) {
                return null;
            }
            tag.putString(IThrowable.ID_TAG, id.toString());
        } else if (item instanceof IMeleeWeapon melee) {
            ResourceLocation id = melee.getId(stack);
            if (id == null || IMeleeWeapon.EMPTY.equals(id)) {
                return null;
            }
            tag.putString(IMeleeWeapon.ID_TAG, id.toString());
        } else {
            return null;
        }
        return tag;
    }

    @Override
    public long recalculateEMC(ItemInfo info, long currentEMC) {
        CompoundTag tag = getPersistentNBT(info);
        if (tag == null) {
            return currentEMC;
        }
        return EMCMappingHandler.getStoredEmcValue(ItemInfo.fromItem(info.getItem(), tag));
    }
}
