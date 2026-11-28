package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.CeaseFireTiming;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.time.GunTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class NoiseState {
    private static final double NEARBY_SHOT_RANGE_SQUARED = 16.0D * 16.0D;
    private static final double NEARBY_FULL_NOISE_RANGE_SQUARED = 4.0D * 4.0D;
    private static final SoundEvent RING = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "fire_noise_ring"));
    private static double value;
    private static long mainStopAt;
    private static long offhandStopAt;
    private static long nearbyStopAt;
    private static LocalPlayer owner;
    private static RingingSound ringing;
    private static volatile float filterIntensity;
    private static float ringVolume;

    private NoiseState() {
    }

    public static void onShot(LocalPlayer player, InteractionHand hand, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> onShot(player, hand, stack));
            return;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        if (player != minecraft.player || !player.isAlive() || gun == null) {
            return;
        }
        updateOwner(player);
        int max = Config.FIRE_NOISE_MAX.get();
        if (max <= 0) {
            return;
        }
        long stopAt = GunTime.nowMillis() + ClientCeaseFireTiming.delay(player, hand, stack, gun);
        if (hand == InteractionHand.MAIN_HAND) {
            mainStopAt = stopAt;
        } else {
            offhandStopAt = stopAt;
        }
        addNoise(TaczFixesDataManager.resolveFireNoise(stack), max);
    }

    public static void onNearbyShot(LivingEntity shooter, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> onNearbyShot(shooter, stack));
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || player == shooter || !player.isAlive() || player.level() != shooter.level()) {
            return;
        }
        double distanceSquared = player.distanceToSqr(shooter);
        if (distanceSquared > NEARBY_SHOT_RANGE_SQUARED) {
            return;
        }
        int max = Config.FIRE_NOISE_MAX.get();
        IGun gun = IGun.getIGunOrNull(stack);
        if (max <= 0 || gun == null) {
            return;
        }
        double attenuation = NEARBY_FULL_NOISE_RANGE_SQUARED / Math.max(NEARBY_FULL_NOISE_RANGE_SQUARED, distanceSquared);
        double noise = TaczFixesDataManager.resolveFireNoise(stack) * attenuation;
        if (noise <= 0.0D) {
            return;
        }
        updateOwner(player);
        long stopAt = GunTime.nowMillis() + CeaseFireTiming.delay(shooter, stack, gun, false);
        nearbyStopAt = Math.max(nearbyStopAt, stopAt);
        addNoise(noise, max);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        updateOwner(player);
        if (player == null || !player.isAlive()) {
            reset();
            return;
        }
        if (minecraft.isPaused() || value <= 0.0D) {
            return;
        }

        int max = Config.FIRE_NOISE_MAX.get();
        value = Math.min(value, max);
        boolean mainHeld = isAutomaticFireHeld(player, InteractionHand.MAIN_HAND);
        boolean offhandHeld = isAutomaticFireHeld(player, InteractionHand.OFF_HAND);
        if (!mainHeld && !offhandHeld
                && GunTime.nowMillis() > Math.max(nearbyStopAt, Math.max(mainStopAt, offhandStopAt))) {
            boolean filterThresholdReached = value >= Config.FIRE_NOISE_FILTER_THRESHOLD.get();
            boolean tinnitusThresholdReached = value >= Config.FIRE_NOISE_TINNITUS_THRESHOLD.get();
            int decay;
            if (filterThresholdReached && tinnitusThresholdReached) {
                decay = Config.FIRE_NOISE_DECAY_PER_TICK_SEVERE.get();
            } else if (filterThresholdReached || tinnitusThresholdReached) {
                decay = Config.FIRE_NOISE_DECAY_PER_TICK_MILD.get();
            } else {
                decay = Config.FIRE_NOISE_DECAY_PER_TICK_UNAFFECTED.get();
            }
            value = Math.max(0.0D, value - decay);
        }
        updateIntensity(max);

        var soundManager = minecraft.getSoundManager();
        if (ringVolume > 0.0F && (ringing == null || !soundManager.isActive(ringing))) {
            ringing = new RingingSound();
            soundManager.play(ringing);
        }
    }

    public static float filterIntensity() {
        return filterIntensity;
    }

    public static void reset() {
        if (ringing != null) {
            Minecraft.getInstance().getSoundManager().stop(ringing);
            ringing = null;
        }
        owner = null;
        value = 0.0D;
        mainStopAt = offhandStopAt = nearbyStopAt = 0L;
        filterIntensity = ringVolume = 0.0F;
    }

    private static void updateOwner(LocalPlayer player) {
        if (owner != player) {
            reset();
            owner = player;
        }
    }

    private static void addNoise(double noise, int max) {
        value = Math.min(max, value + noise);
        updateIntensity(max);
    }

    private static boolean isAutomaticFireHeld(LocalPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        IGun gun = IGun.getIGunOrNull(stack);
        return ClientCeaseFireTiming.isAutomaticFireHeld(player, hand, stack, gun);
    }

    private static void updateIntensity(int max) {
        filterIntensity = Config.FIRE_NOISE_FILTER_STRENGTH.get().floatValue()
                * intensity(Config.FIRE_NOISE_FILTER_THRESHOLD.get(), max);
        ringVolume = Config.FIRE_NOISE_TINNITUS_STRENGTH.get().floatValue()
                * intensity(Config.FIRE_NOISE_TINNITUS_THRESHOLD.get(), max);
    }

    private static float intensity(int threshold, int max) {
        if (value <= threshold || max <= threshold) {
            return 0.0F;
        }
        return (float) ((value - threshold) / (max - threshold));
    }

    private static final class RingingSound extends AbstractTickableSoundInstance {
        private RingingSound() {
            super(RING, SoundSource.PLAYERS, RandomSource.create());
            volume = ringVolume;
            looping = true;
            relative = true;
            attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
            volume = ringVolume;
            if (volume <= 0.0F) {
                stop();
            }
        }
    }
}

