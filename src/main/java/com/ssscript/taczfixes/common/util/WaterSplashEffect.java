package com.ssscript.taczfixes.common.util;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class WaterSplashEffect {
    private WaterSplashEffect() {
    }

    public static void spawn(ServerLevel level, Vec3 position, double speed) {
        int count = Mth.clamp((int) (8.0 + speed * 2.0), 8, 32);
        level.sendParticles(ParticleTypes.SPLASH,
                position.x, position.y + 0.05, position.z,
                count, 0.2, 0.3, 0.2, 0.1);
        level.sendParticles(ParticleTypes.BUBBLE,
                position.x, position.y, position.z,
                Math.max(2, count / 3), 0.1, 0.15, 0.1, 0.05);
        level.playSound(null, position.x, position.y, position.z,
                SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL,
                1.0F, 0.85F + level.random.nextFloat() * 0.3F);
    }
}
