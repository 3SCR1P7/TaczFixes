package com.ssscript.taczfixes.common.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AttachmentGroupOffsetManager {
    private static final Map<ResourceLocation, Map<String, float[]>> OFFSETS = new ConcurrentHashMap<>();

    private AttachmentGroupOffsetManager() {
    }

    public static void putAll(Map<ResourceLocation, Map<String, float[]>> map) {
        OFFSETS.clear();
        OFFSETS.putAll(map);
    }

    public static float[] getOffset(ResourceLocation attachmentId, String slotTypeName) {
        if (attachmentId == null || slotTypeName == null) return null;
        Map<String, float[]> offsets = OFFSETS.get(attachmentId);
        return offsets == null ? null : offsets.get(slotTypeName);
    }
}