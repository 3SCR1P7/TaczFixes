package com.ssscript.taczfixes.common.util;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/util/DualWieldStackId.class */
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
}
