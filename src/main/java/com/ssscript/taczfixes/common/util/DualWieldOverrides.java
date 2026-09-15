package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class DualWieldOverrides {

    public record Value(Boolean enable, Double recoilMultiplier, Double leftOffset, Double rightOffset) {
    }

    public interface Provider {
        Value resolve(ResourceLocation gunId);
    }

    private static volatile Provider provider;

    private DualWieldOverrides() {
    }

    public static void setProvider(Provider newProvider) {
        provider = newProvider;
    }

    public static Value resolve(ResourceLocation gunId) {
        Provider current = provider;
        if (current == null || gunId == null) {
            return null;
        }
        return current.resolve(gunId);
    }

    private static Value resolveStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return null;
        }
        return resolve(gun.getGunId(stack));
    }

    public static Boolean enabled(ResourceLocation gunId) {
        Value value = resolve(gunId);
        return value == null ? null : value.enable();
    }

    public static Double recoilMultiplier(ItemStack stack) {
        Value value = resolveStack(stack);
        return value == null ? null : value.recoilMultiplier();
    }

    public static double leftOffset(ItemStack stack, double fallback) {
        Value value = resolveStack(stack);
        Double offset = value == null ? null : value.leftOffset();
        return offset != null && Double.isFinite(offset.doubleValue()) ? offset.doubleValue() : fallback;
    }

    public static double rightOffset(ItemStack stack, double fallback) {
        Value value = resolveStack(stack);
        Double offset = value == null ? null : value.rightOffset();
        return offset != null && Double.isFinite(offset.doubleValue()) ? offset.doubleValue() : fallback;
    }
}
