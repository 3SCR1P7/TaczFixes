package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.Config;
import com.ssscript.taczfixes.common.data.InaccuracyParams;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SpreadState {
    private static final Map<ResourceLocation, Entry> STATES = new ConcurrentHashMap<>();

    private static class Entry {
        int stacks = 0;
        long lastShotTime = 0;
        InaccuracyParams params = null;
    }

    public static void onShot(ResourceLocation gunId, InaccuracyParams params) {
        if (gunId == null || params == null) return;
        Entry entry = STATES.computeIfAbsent(gunId, k -> new Entry());
        entry.stacks = Math.min(entry.stacks + 1, params.maxStack);
        entry.lastShotTime = System.currentTimeMillis();
        entry.params = params;
    }

    public static float modifyInaccuracy(ResourceLocation gunId, InaccuracyParams params, float baseInaccuracy) {
        if (gunId == null) return baseInaccuracy;
        Entry entry = STATES.get(gunId);
        int currentStacks = entry == null ? 0 : Math.max(0, entry.stacks - 1);
        double shotPercent = params != null ? params.shotPercent : Config.SPREAD_RAMP_INCREMENT.get();
        double shotAddend = params != null ? params.shotAddend : Config.SPREAD_RAMP_FLAT_INCREMENT.get();
        float percMultiplier = 1.0f + (float) (currentStacks * shotPercent);
        float flatAdd = (float) (currentStacks * shotAddend);
        return baseInaccuracy * percMultiplier + flatAdd;
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        for (Entry entry : STATES.values()) {
            if (entry.stacks <= 0) continue;
            InaccuracyParams params = entry.params;
            if (params == null) continue;
            if (now - entry.lastShotTime >= params.cooldownDelay) {
                int stacksToRemove;
                if (params.shotPercent <= 0) {
                    stacksToRemove = 1;
                } else {
                    stacksToRemove = (int) Math.ceil(params.cooldownSpeed / params.shotPercent);
                    if (stacksToRemove < 1) stacksToRemove = 1;
                }
                entry.stacks = Math.max(0, entry.stacks - stacksToRemove);
            }
        }
    }
}
