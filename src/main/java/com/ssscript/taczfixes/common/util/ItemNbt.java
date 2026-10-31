package com.ssscript.taczfixes.common.util;

import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

public final class ItemNbt {
    private ItemNbt() {
    }

    @Nullable
    public static CompoundTag get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        return data.getUnsafe();
    }

    public static CompoundTag getOrCreate(ItemStack stack) {
        CompoundTag tag = get(stack);
        if (tag != null) {
            return tag;
        }
        CompoundTag created = new CompoundTag();
        if (stack != null && !stack.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(created));
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null) {
                return data.getUnsafe();
            }
        }
        return created;
    }

    public static void set(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    public static CompoundTag saveStack(ItemStack stack) {
        HolderLookup.Provider provider = RegistryAccessHelper.provider();
        if (provider == null || stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        net.minecraft.nbt.Tag saved = stack.save(provider);
        return saved instanceof CompoundTag compound ? compound.copy() : new CompoundTag();
    }

    public static ItemStack parseStack(CompoundTag tag) {
        HolderLookup.Provider provider = RegistryAccessHelper.provider();
        if (provider == null || tag == null) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(provider, tag);
    }

    public static void mutate(ItemStack stack, Consumer<CompoundTag> action) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(action));
    }
}

