package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.common.util.ItemNbt;
import me.xjqsh.lrtactical.api.item.IConsumable;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.item.IThrowable;
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

@DataComponentProcessor(priority = 1000, requiredMods = {"projecte", "lrtactical"})
public class LrtacticalNbtEMCProcessor implements IDataComponentProcessor {
    @Override
    public String getTranslationKey() {
        return "projecte.gun_nbt_processor.taczfixes.lrtactical";
    }


    @Override
    public String getName() {
        return "LrtacticalIdNbtProcessor";
    }

    @Override
    public String getDescription() {
        return "Keeps only the LRTactical subtype id in ProjectE knowledge.";
    }

    @Override
    public boolean hasPersistentComponents() {
        return true;
    }

    private static CompoundTag persistentTag(ItemInfo info) {
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
    public void collectPersistentComponents(@NotNull ItemInfo info, DataComponentPatch.Builder builder) {
        CompoundTag tag = persistentTag(info);
        if (tag != null) {
            builder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public long recalculateEMC(@NotNull ItemInfo info, long currentEMC) {
        CompoundTag tag = persistentTag(info);
        if (tag == null) {
            return currentEMC;
        }
        ItemStack stack = info.createStack();
        ItemNbt.set(stack, tag);
        return EMCMappingHandler.getStoredEmcValue(ItemInfo.fromStack(stack));
    }
}
