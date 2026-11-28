package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class CeaseFireTiming {
    private static final long GRACE_MS = 50L;
    private static final long MIN_DELAY_MS = 200L;

    private CeaseFireTiming() {
    }

    public static long delay(LivingEntity shooter, ItemStack stack, IGun gun, boolean useShooterCache) {
        GunData data = TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                .map(CommonGunIndex::getGunData).orElse(null);
        if (data != null) {
            FireMode mode = gun.getFireMode(stack);
            if (mode == FireMode.BURST) {
                return afterInterval(data.getBurstShootInterval());
            }
            if (useShooterCache) {
                return afterInterval(data.getShootInterval(shooter, mode, stack));
            }
        }
        int rpm = gun.getRPM(stack);
        return afterInterval(60_000L / (rpm > 0 ? rpm : 300));
    }

    public static long afterInterval(long intervalMs) {
        return Math.max(MIN_DELAY_MS, intervalMs + GRACE_MS);
    }
}
