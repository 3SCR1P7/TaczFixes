package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import net.minecraft.world.item.ItemStack;

/** 枪械电量(FE)存储。 */
public final class ChargeStorage {
    public static final String TAG_KEY = "TaczFixesCharge";

    private ChargeStorage() {
    }

    public static GunTaczFixesData.ChargeConfig config(ItemStack stack) {
        return TaczFixesDataManager.resolveCharge(stack);
    }

    public static int getMax(ItemStack stack) {
        GunTaczFixesData.ChargeConfig cfg = config(stack);
        if (cfg == null || cfg.power_max == null) {
            return 0;
        }
        return Math.max(0, cfg.power_max);
    }

    public static boolean isChargeGun(ItemStack stack) {
        return getMax(stack) > 0;
    }

    public static int get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int max = getMax(stack);
        if (max <= 0) {
            return 0;
        }
        int value = stack.getOrCreateTag().getInt(TAG_KEY);
        return Math.max(0, Math.min(value, max));
    }

    public static void set(ItemStack stack, int value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int max = getMax(stack);
        if (max <= 0) {
            return;
        }
        stack.getOrCreateTag().putInt(TAG_KEY, Math.max(0, Math.min(value, max)));
    }

    /** 返回实际接收到的电量。 */
    public static int add(ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) {
            return 0;
        }
        int max = getMax(stack);
        if (max <= 0) {
            return 0;
        }
        int current = get(stack);
        int accepted = Math.min(amount, max - current);
        if (accepted > 0) {
            set(stack, current + accepted);
        }
        return accepted;
    }

    /** 尝试消耗电量; 电量不足时返回 false 且不消耗。 */
    public static boolean consume(ItemStack stack, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        int max = getMax(stack);
        if (max <= 0) {
            return false;
        }
        int current = get(stack);
        if (current < amount) {
            return false;
        }
        set(stack, current - amount);
        return true;
    }
}
