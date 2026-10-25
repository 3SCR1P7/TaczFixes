package com.ssscript.taczfixes.client.client;

import com.ssscript.taczfixes.common.data.InaccuracyParams;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.SpreadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class CrosshairSpreadRamp {
    private static final long SERVER_TICK_MS = 50L;
    private static final Map<ResourceLocation, Entry> ENTRIES = new HashMap<>();
    private static UUID owner;

    private static final class Entry {
        int stacks;
        long lastShotTime;
        InaccuracyParams params;
    }

    private CrosshairSpreadRamp() {
    }

    public static void onShot(LocalPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return;
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return;
        ResourceLocation gunId = gun.getGunId(stack);
        if (gunId == null) return;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        InaccuracyType state = InaccuracyType.getInaccuracyType(player);
        InaccuracyParams params = TaczFixesDataManager.resolveInaccuracyParams(dataId, state, stack);
        if (params == null) return;
        ensureOwner(player);
        Entry entry = ENTRIES.computeIfAbsent(gunId, key -> new Entry());
        entry.stacks = Math.min(entry.stacks + 1, Math.max(0, params.maxStack));
        entry.lastShotTime = System.currentTimeMillis();
        entry.params = params;
    }

    public static float modifyInaccuracy(LocalPlayer player, ResourceLocation gunId, float inaccuracy) {
        if (player == null || gunId == null) return inaccuracy;
        ensureOwner(player);
        Entry entry = ENTRIES.get(gunId);
        if (entry == null) return inaccuracy;
        decay(entry, System.currentTimeMillis());
        if (entry.stacks <= 0) return inaccuracy;
        return SpreadState.applyPenalty(inaccuracy, entry.stacks - 1, entry.params);
    }

    private static void decay(Entry entry, long now) {
        InaccuracyParams params = entry.params;
        if (params == null || entry.stacks <= 0) return;
        long elapsed = now - entry.lastShotTime;
        if (elapsed < params.cooldownDelay) return;
        long ticks = (elapsed - params.cooldownDelay) / SERVER_TICK_MS;
        if (ticks <= 0) return;
        int perTick = 1;
        if (params.shotPercent > 0.0) {
            perTick = (int) Math.ceil(params.cooldownSpeed / params.shotPercent);
            if (perTick < 1) perTick = 1;
        }
        long removal = ticks * (long) perTick;
        entry.stacks = (int) Math.max(0L, entry.stacks - Math.min(removal, entry.stacks));
        entry.lastShotTime += ticks * SERVER_TICK_MS;
    }

    private static void ensureOwner(LocalPlayer player) {
        UUID id = player.getUUID();
        if (!id.equals(owner)) {
            ENTRIES.clear();
            owner = id;
        }
    }
}
