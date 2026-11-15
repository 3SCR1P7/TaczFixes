package com.ssscript.taczfixes.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/** 枪械贴图调色板: 每个颜色分组的自标色(RGB, -1 = 保持原色); 同时保存动态迷彩 id。 */
public final class GunColorStorage {
    public static final String TAG_KEY = "TaczFixesGunColor";
    private static final String TARGETS_KEY = "targets";
    private static final String CAMO_KEY = "camo";
    public static final int MAX_CLUSTERS = 32;
    /** 迷彩 id 最长长度(资源位置完整写法), 与网络包上限一致。 */
    public static final int MAX_CAMO_LENGTH = 128;

    private GunColorStorage() {
    }

    public static int[] get(ItemStack gun) {
        CompoundTag color = colorTag(gun);
        if (color == null) return new int[0];
        int[] targets = color.getIntArray(TARGETS_KEY);
        return targets.length > MAX_CLUSTERS ? Arrays.copyOf(targets, MAX_CLUSTERS) : targets;
    }

    /** 只改调色板, 保留已选的动态迷彩。 */
    public static void set(ItemStack gun, int[] targets) {
        if (gun == null || gun.isEmpty()) return;
        int[] safe = targets == null ? new int[0]
                : Arrays.copyOf(targets, Math.min(targets.length, MAX_CLUSTERS));
        CompoundTag tag = gun.getOrCreateTag();
        CompoundTag existing = colorTag(gun);
        CompoundTag color = existing != null ? existing.copy() : new CompoundTag();
        color.putIntArray(TARGETS_KEY, safe);
        tag.put(TAG_KEY, color);
    }

    /** 当前应用的动态迷彩 id, 空串表示无迷彩。 */
    public static String getCamo(ItemStack stack) {
        CompoundTag color = colorTag(stack);
        if (color == null || !color.contains(CAMO_KEY, 8)) return "";
        String camo = color.getString(CAMO_KEY);
        return camo.length() > MAX_CAMO_LENGTH ? "" : camo;
    }

    /** 设置/清除动态迷彩, 保留调色板。 */
    public static void setCamo(ItemStack stack, String camoId) {
        if (stack == null || stack.isEmpty()) return;
        String safe = camoId == null ? "" : camoId;
        if (safe.length() > MAX_CAMO_LENGTH) {
            safe = safe.substring(0, MAX_CAMO_LENGTH);
        }
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag existing = colorTag(stack);
        CompoundTag color = existing != null ? existing.copy() : new CompoundTag();
        if (safe.isEmpty()) {
            color.remove(CAMO_KEY);
        } else {
            color.putString(CAMO_KEY, safe);
        }
        if (color.isEmpty()) {
            tag.remove(TAG_KEY);
        } else {
            tag.put(TAG_KEY, color);
        }
    }

    private static CompoundTag colorTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_KEY, 10)) return null;
        return tag.getCompound(TAG_KEY);
    }
}
