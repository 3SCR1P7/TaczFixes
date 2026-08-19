package com.ssscript.taczfixes.common.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SwitchedDisplayDataManager {
    private static final Map<ResourceLocation, List<ResourceLocation>> SWITCHED = new ConcurrentHashMap<>();

    private SwitchedDisplayDataManager() {
    }

    public static void putAll(Map<ResourceLocation, List<ResourceLocation>> map) {
        SWITCHED.clear();
        SWITCHED.putAll(map);
    }

    public static Map<ResourceLocation, List<ResourceLocation>> getAll() {
        return SWITCHED;
    }
}