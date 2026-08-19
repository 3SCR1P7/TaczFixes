package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecoilMultiplierResolver {
    private static final Map<GunTaczFixesData.RecoilConfig, Channel> CHANNELS = new HashMap<>();

    private static class Channel {
        int shotCount;
        long lastShotTime;
    }

    private RecoilMultiplierResolver() {
    }

    public static boolean isActive(GunTaczFixesData.RecoilConfig config) {
        return config != null
                && ((config.modifiers != null && !config.modifiers.isEmpty())
                || config.pitch_multiplier != null || config.yaw_multiplier != null);
    }

    public static float[] advance(List<GunTaczFixesData.RecoilConfig> configs, long now) {
        if (configs == null || configs.isEmpty()) {
            CHANNELS.clear();
            return new float[]{1.0f, 1.0f};
        }
        CHANNELS.keySet().retainAll(configs);
        float pitch = 1.0f;
        float yaw = 1.0f;
        for (GunTaczFixesData.RecoilConfig config : configs) {
            if (!isActive(config)) continue;
            Channel channel = CHANNELS.computeIfAbsent(config, k -> new Channel());
            long window = config.window != null ? config.window : 0;
            long elapsed = now - channel.lastShotTime;
            channel.shotCount = (elapsed < 0 || elapsed >= window) ? 1 : channel.shotCount + 1;
            channel.lastShotTime = now;
            float[] multipliers = resolveMultipliers(config, channel.shotCount);
            pitch *= multipliers[0];
            yaw *= multipliers[1];
        }
        return new float[]{pitch, yaw};
    }

    public static float[] peek(List<GunTaczFixesData.RecoilConfig> configs, long now) {
        if (configs == null || configs.isEmpty()) {
            return new float[]{1.0f, 1.0f};
        }
        float pitch = 1.0f;
        float yaw = 1.0f;
        for (GunTaczFixesData.RecoilConfig config : configs) {
            if (!isActive(config)) continue;
            Channel channel = CHANNELS.get(config);
            long window = config.window != null ? config.window : 0;
            long elapsed = now - (channel == null ? 0 : channel.lastShotTime);
            int count = (channel == null || elapsed < 0 || elapsed >= window) ? 1 : channel.shotCount + 1;
            float[] multipliers = resolveMultipliers(config, count);
            pitch *= multipliers[0];
            yaw *= multipliers[1];
        }
        return new float[]{pitch, yaw};
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
            if (count < mod.count_start) return false;
            if (mod.count_end != null && count > mod.count_end) return false;
            int step = mod.count_step != null ? mod.count_step : 1;
            return step <= 1 || (count - mod.count_start) % step == 0;
        }
        return false;
    }
}