package com.ssscript.taczfixes.common.util;

import net.minecraft.world.item.ItemStack;

/** Lua setRecoilMultiplier 设置的逐枪开火后坐力倍率。缺省为 1.0。 */
public final class RecoilMultiplierStorage {
    public static final String TAG_KEY = "TaczFixesRecoilMultiplier";

    private RecoilMultiplierStorage() {
    }

    public static float get(ItemStack stack) {
        if (stack == null || stack.isEmpty() || ItemNbt.get(stack) == null
                || !ItemNbt.get(stack).contains(TAG_KEY)) {
            return 1.0f;
        }
        return ItemNbt.get(stack).getFloat(TAG_KEY);
    }

    public static void set(ItemStack stack, float value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemNbt.getOrCreate(stack).putFloat(TAG_KEY, value);
    }
}
