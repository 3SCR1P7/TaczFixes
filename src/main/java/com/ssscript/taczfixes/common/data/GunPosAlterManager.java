package com.ssscript.taczfixes.common.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GunPosAlterManager {
    private static final Map<ResourceLocation, Map<String, float[]>> RANGES = new ConcurrentHashMap<>();

    private GunPosAlterManager() {
    }

    public static void putAll(Map<ResourceLocation, Map<String, float[]>> map) {
        RANGES.clear();
        RANGES.putAll(map);
    }

    public static float[] getRange(ResourceLocation gunId, String slotKey) {
        if (gunId == null || slotKey == null) return null;
        Map<String, float[]> ranges = RANGES.get(gunId);
        return ranges == null ? null : ranges.get(slotKey);
    }
}