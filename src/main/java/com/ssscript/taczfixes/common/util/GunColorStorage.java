package com.ssscript.taczfixes.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/** 枪械贴图调色板: 每个颜色分组的自标色(RGB, -1 = 保持原色)。 */
public final class GunColorStorage {
    public static final String TAG_KEY = "TaczFixesGunColor";
    private static final String TARGETS_KEY = "targets";
    public static final int MAX_CLUSTERS = 32;

    private GunColorStorage() {
    }

    public static int[] get(ItemStack gun) {
        if (gun == null || gun.isEmpty()) return new int[0];
        CompoundTag tag = gun.getTag();
        if (tag == null || !tag.contains(TAG_KEY, 10)) return new int[0];
        int[] targets = tag.getCompound(TAG_KEY).getIntArray(TARGETS_KEY);
        return targets.length > MAX_CLUSTERS ? Arrays.copyOf(targets, MAX_CLUSTERS) : targets;
    }

    public static void set(ItemStack gun, int[] targets) {
        if (gun == null || gun.isEmpty()) return;
        int[] safe = targets == null ? new int[0]
                : Arrays.copyOf(targets, Math.min(targets.length, MAX_CLUSTERS));
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag color = new CompoundTag();
        color.putIntArray(TARGETS_KEY, safe);
        tag.put(TAG_KEY, color);
    }
}
