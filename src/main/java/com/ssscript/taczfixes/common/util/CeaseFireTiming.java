package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class CeaseFireTiming {
    private static final long GRACE_MS = 50L;
    private static final long MIN_DELAY_MS = 200L;

    private CeaseFireTiming() {
    }

    public static long delay(LivingEntity shooter, ItemStack stack, IGun gun, boolean useShooterCache) {
        int rpm = gun.getRPM(stack);
        return afterInterval(60_000L / (rpm > 0 ? rpm : 300));
    }

    public static long afterInterval(long intervalMs) {
        return Math.max(MIN_DELAY_MS, intervalMs + GRACE_MS);
    }
}
