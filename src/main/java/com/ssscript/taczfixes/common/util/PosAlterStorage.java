package com.ssscript.taczfixes.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class PosAlterStorage {
    public static final String TAG_KEY = "TaczFixesPosAlter";

    private PosAlterStorage() {
    }

    public static float get(ItemStack gun, String slotKey) {
        if (gun == null || gun.isEmpty()) return 0.0F;
        CompoundTag tag = gun.getTag();
        if (tag == null || !tag.contains(TAG_KEY, 10)) return 0.0F;
        CompoundTag alters = tag.getCompound(TAG_KEY);
        if (!alters.contains(slotKey, 5)) return 0.0F;
        return alters.getFloat(slotKey);
    }

    public static void set(ItemStack gun, String slotKey, float value) {
        if (gun == null || gun.isEmpty()) return;
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag alters = tag.contains(TAG_KEY, 10) ? tag.getCompound(TAG_KEY) : new CompoundTag();
        alters.putFloat(slotKey, value);
        tag.put(TAG_KEY, alters);
    }
}