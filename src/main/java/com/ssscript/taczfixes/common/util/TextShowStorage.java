package com.ssscript.taczfixes.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class TextShowStorage {
    public static final String TAG_KEY = "TaczFixesTextShow";

    private TextShowStorage() {
    }

    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.trim();
        while (normalized.startsWith("%")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("%")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.trim();
    }

    public static void set(ItemStack stack, String name, String value) {
        String key = normalizeName(name);
        if (stack == null || stack.isEmpty() || key.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag values = tag.contains(TAG_KEY, 10) ? tag.getCompound(TAG_KEY) : new CompoundTag();
        values.putString(key, value == null ? "" : value);
        tag.put(TAG_KEY, values);
    }

    public static String get(ItemStack stack, String name) {
        String key = normalizeName(name);
        if (stack == null || stack.isEmpty() || key.isEmpty()) {
            return "";
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_KEY, 10)) {
            return "";
        }
        CompoundTag values = tag.getCompound(TAG_KEY);
        return values.contains(key, 8) ? values.getString(key) : "";
    }
}
