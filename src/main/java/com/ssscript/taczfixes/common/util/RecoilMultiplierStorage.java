package com.ssscript.taczfixes.common.util;

import net.minecraft.world.item.ItemStack;

/** Lua setRecoilMultiplier 设置的逐枪开火后坐力倍率。缺省为 1.0。 */
public final class RecoilMultiplierStorage {
    public static final String TAG_KEY = "TaczFixesRecoilMultiplier";

    private RecoilMultiplierStorage() {
    }

    public static float get(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null
                || !stack.getTag().contains(TAG_KEY)) {
            return 1.0f;
        }
        return stack.getTag().getFloat(TAG_KEY);
    }

    public static void set(ItemStack stack, float value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.getOrCreateTag().putFloat(TAG_KEY, value);
    }
}
