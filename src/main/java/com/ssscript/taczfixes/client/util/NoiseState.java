package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
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
import net.minecraft.world.item.ItemStack;

public final class NoiseState {
    private static final SoundEvent RING = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "fire_noise_ring"));
    private static double value;
    private static long mainStopAt;
    private static long offhandStopAt;
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
        value = Math.min(max, value + TaczFixesDataManager.resolveFireNoise(stack));
        updateIntensity(max);
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
        if (GunTime.nowMillis() >= Math.max(mainStopAt, offhandStopAt)) {
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
        mainStopAt = offhandStopAt = 0L;
        filterIntensity = ringVolume = 0.0F;
    }

    private static void updateOwner(LocalPlayer player) {
        if (owner != player) {
            reset();
            owner = player;
        }
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

