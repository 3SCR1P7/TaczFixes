package com.ssscript.taczfixes.common.util;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class DualWieldStackId {
    private static final String TAG_KEY = "taczfixes:stack_id";

    private DualWieldStackId() {
    }

    public static UUID get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(TAG_KEY)) {
            return null;
        }
        return tag.getUUID(TAG_KEY);
    }

    public static UUID getOrCreate(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID(TAG_KEY)) {
            tag.putUUID(TAG_KEY, UUID.randomUUID());
        }
        return tag.getUUID(TAG_KEY);
    }

    public static UUID regenerate(ItemStack stack) {
        UUID stackId = UUID.randomUUID();
        stack.getOrCreateTag().putUUID(TAG_KEY, stackId);
        return stackId;
    }

    /** 两个 ItemStack 是否代表同一把枪(优先按 stack id 比较, 兼容网络同步出的副本)。 */
    public static boolean matches(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
            return first != null && second != null && first.isEmpty() && second.isEmpty();
        }
        UUID firstId = get(first);
        UUID secondId = get(second);
        if (firstId != null && secondId != null) {
            return firstId.equals(secondId);
        }
        com.tacz.guns.api.item.IGun firstGun = com.tacz.guns.api.item.IGun.getIGunOrNull(first);
        com.tacz.guns.api.item.IGun secondGun = com.tacz.guns.api.item.IGun.getIGunOrNull(second);
        return firstGun != null && secondGun != null && first.getItem() == second.getItem()
                && firstGun.getGunId(first).equals(secondGun.getGunId(second));
    }
}
