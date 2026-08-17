package com.ssscript.taczfixes.util;

import com.ssscript.taczfixes.data.GunTaczFixesData;

public class RecoilMultiplierResolver {
    private static int shotCount;
    private static long lastShotTime;

    private RecoilMultiplierResolver() {
    }

    public static boolean isActive(GunTaczFixesData.RecoilConfig config) {
        return config != null
                && ((config.modifiers != null && !config.modifiers.isEmpty())
                || config.pitch_multiplier != null || config.yaw_multiplier != null);
    }

    public static float[] advance(GunTaczFixesData.RecoilConfig config, long now) {
        if (!isActive(config)) {
            shotCount = 0;
            lastShotTime = now;
            return new float[]{1.0f, 1.0f};
        }
        long window = config.window != null ? config.window : 0;
        long elapsed = now - lastShotTime;
        shotCount = (elapsed < 0 || elapsed >= window) ? 1 : shotCount + 1;
        lastShotTime = now;
        return resolveMultipliers(config, shotCount);
    }

    public static float[] peek(GunTaczFixesData.RecoilConfig config, long now) {
        if (!isActive(config)) {
            return new float[]{1.0f, 1.0f};
        }
        long window = config.window != null ? config.window : 0;
        long elapsed = now - lastShotTime;
        int count = (elapsed < 0 || elapsed >= window) ? 1 : shotCount + 1;
        return resolveMultipliers(config, count);
    }

    private static float[] resolveMultipliers(GunTaczFixesData.RecoilConfig config, int count) {
        Double pitch = null;
        Double yaw = null;
        if (config.modifiers != null && !config.modifiers.isEmpty()) {
            for (GunTaczFixesData.RecoilModifierConfig mod : config.modifiers.values()) {
                if (mod != null && shotInRange(mod, count)) {
                    pitch = mod.pitch_multiplier;
                    yaw = mod.yaw_multiplier;
                    break;
                }
            }
        } else if (count == 1) {
            pitch = config.pitch_multiplier;
            yaw = config.yaw_multiplier;
        }
        return new float[]{
                pitch != null ? pitch.floatValue() : 1.0f,
                yaw != null ? yaw.floatValue() : 1.0f
        };
    }

    private static boolean shotInRange(GunTaczFixesData.RecoilModifierConfig mod, int count) {
        if (mod.count != null) {
            return mod.count == count;
        }
        if (mod.count_start != null) {
            return count >= mod.count_start && (mod.count_end == null || count <= mod.count_end);
        }
        return false;
    }
}