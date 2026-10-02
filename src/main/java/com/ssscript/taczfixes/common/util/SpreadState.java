package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.InaccuracyParams;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpreadState {
    private static final Map<UUID, Map<ResourceLocation, Entry>> STATES = new ConcurrentHashMap<>();

    private static class Entry {
        final WeakReference<LivingEntity> shooter;
        int stacks = 0;
        long lastShotTime = 0;
        InaccuracyParams params = null;

        Entry(LivingEntity shooter) {
            this.shooter = new WeakReference<>(shooter);
        }
    }

    public static void onShot(LivingEntity shooter, ResourceLocation gunId, InaccuracyParams params) {
        if (shooter == null || gunId == null || params == null) return;
        Entry entry = STATES.computeIfAbsent(shooter.getUUID(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(gunId, k -> new Entry(shooter));
        entry.stacks = Math.min(entry.stacks + 1, params.maxStack);
        entry.lastShotTime = System.currentTimeMillis();
        entry.params = params;
    }

    public static float modifyInaccuracy(LivingEntity shooter, ResourceLocation gunId, InaccuracyParams params, float baseInaccuracy) {
        if (shooter == null || gunId == null) return baseInaccuracy;
        Map<ResourceLocation, Entry> perGun = STATES.get(shooter.getUUID());
        Entry entry = perGun == null ? null : perGun.get(gunId);
        int currentStacks = entry == null ? 0 : Math.max(0, entry.stacks - 1);
        double shotPercent = params != null ? params.shotPercent : Config.SPREAD_RAMP_INCREMENT.get();
        double shotAddend = params != null ? params.shotAddend : Config.SPREAD_RAMP_FLAT_INCREMENT.get();
        float percMultiplier = 1.0f + (float) (currentStacks * shotPercent);
        float flatAdd = (float) (currentStacks * shotAddend);
        return baseInaccuracy * percMultiplier + flatAdd;
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            STATES.remove(playerId);
        }
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Map<ResourceLocation, Entry>>> players = STATES.entrySet().iterator();
        while (players.hasNext()) {
            Map<ResourceLocation, Entry> perGun = players.next().getValue();
            Iterator<Map.Entry<ResourceLocation, Entry>> guns = perGun.entrySet().iterator();
            while (guns.hasNext()) {
                Entry entry = guns.next().getValue();
                LivingEntity shooter = entry.shooter.get();
                if (shooter == null || !shooter.isAlive()) {
                    guns.remove();
                    continue;
                }
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
            if (perGun.isEmpty()) {
                players.remove();
            }
        }
    }
}
