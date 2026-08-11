package com.ssscript.taczfixes.util;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OverloadDamage {
    private static final Map<UUID, Float> FACTORS = new ConcurrentHashMap<>();

    private OverloadDamage() {
    }

    public static void setFactor(LivingEntity shooter, float factor) {
        UUID key = shooter.getUUID();
        if (factor <= 1.0F) {
            FACTORS.remove(key);
        } else {
            FACTORS.put(key, factor);
        }
    }

    public static float getFactor(LivingEntity shooter) {
        if (shooter == null) {
            return 1.0F;
        }
        return FACTORS.getOrDefault(shooter.getUUID(), 1.0F);
    }
}