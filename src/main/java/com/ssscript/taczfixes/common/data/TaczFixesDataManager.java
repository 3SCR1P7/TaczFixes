package com.ssscript.taczfixes.common.data;

import com.ssscript.taczfixes.common.Config;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TaczFixesDataManager {
    private static final Map<ResourceLocation, GunTaczFixesData> DATA = new ConcurrentHashMap<>();

    private TaczFixesDataManager() {
    }

    public static void putAll(Map<ResourceLocation, GunTaczFixesData> map) {
        DATA.clear();
        DATA.putAll(map);
    }

    public static GunTaczFixesData get(ResourceLocation dataId) {
        return DATA.get(dataId);
    }

    public static ResourceLocation resolveDataId(ResourceLocation gunId) {
        if (gunId == null) return null;
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getPojo().getData())
                .orElse(gunId);
    }

    public static Double getLimbFactor(ResourceLocation dataId) {
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        return data == null ? null : data.limb_factor;
    }

    public static GunTaczFixesData.RecoilConfig resolveRecoil(ResourceLocation dataId, FireMode mode) {
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        if (data == null || data.recoil_multiplier == null || data.recoil_multiplier.isEmpty()) {
            return null;
        }
        String key = switch (mode) {
            case AUTO -> "auto";
            case SEMI -> "semi";
            case BURST -> "burst";
            case UNKNOWN -> null;
        };
        return key == null ? null : data.recoil_multiplier.get(key);
    }

    public static GunTaczFixesData.FireKnockbackConfig resolveFireKnockback(ResourceLocation dataId) {
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        return data == null ? null : data.fire_knockback;
    }

    public static InaccuracyParams resolveInaccuracyParams(ResourceLocation dataId, InaccuracyType state) {
        GunTaczFixesData data = dataId == null ? null : DATA.get(dataId);
        if (data == null || data.inaccuracy_multiplier == null || data.inaccuracy_multiplier.isEmpty()) {
            return fromConfig();
        }
        String key = state == null ? "stand" : state.name().toLowerCase(Locale.ROOT);
        GunTaczFixesData.InaccuracyConfig cfg = data.inaccuracy_multiplier.get(key);
        GunTaczFixesData.InaccuracyConfig stand = data.inaccuracy_multiplier.get("stand");

        boolean cfgEnabled = Config.SPREAD_RAMP_ENABLED.get();
        int maxStack = pick(cfg == null ? null : cfg.max_stack,
                stand == null ? null : stand.max_stack, cfgEnabled ? Config.SPREAD_RAMP_MAX_STACKS.get() : 0);
        long cooldownDelay = pick(cfg == null ? null : cfg.cooldown_delay,
                stand == null ? null : stand.cooldown_delay, Config.SPREAD_RAMP_DECAY_DELAY_MS.get());
        double cooldownSpeed = pick(cfg == null ? null : cfg.cooldown_speed,
                stand == null ? null : stand.cooldown_speed, Config.SPREAD_RAMP_DECAY.get());
        double shotPercent = pick(cfg == null ? null : cfg.shot_percent,
                stand == null ? null : stand.shot_percent, cfgEnabled ? Config.SPREAD_RAMP_INCREMENT.get() : 0.0);
        double shotAddend = pick(cfg == null ? null : cfg.shot_addend,
                stand == null ? null : stand.shot_addend, cfgEnabled ? Config.SPREAD_RAMP_FLAT_INCREMENT.get() : 0.0);
        return new InaccuracyParams(maxStack, cooldownDelay, cooldownSpeed, shotPercent, shotAddend);
    }

    public static InaccuracyParams resolveInaccuracyParams(ResourceLocation dataId, InaccuracyType state, ItemStack gunItem) {
        InaccuracyParams base = resolveInaccuracyParams(dataId, state);
        if (gunItem == null) return base;
        return AttachmentTaczFixesManager.adjustInaccuracy(gunItem, base);
    }

    private static InaccuracyParams fromConfig() {
        boolean enabled = Config.SPREAD_RAMP_ENABLED.get();
        return new InaccuracyParams(
                enabled ? Config.SPREAD_RAMP_MAX_STACKS.get() : 0,
                Config.SPREAD_RAMP_DECAY_DELAY_MS.get(),
                Config.SPREAD_RAMP_DECAY.get(),
                enabled ? Config.SPREAD_RAMP_INCREMENT.get() : 0.0,
                enabled ? Config.SPREAD_RAMP_FLAT_INCREMENT.get() : 0.0);
    }

    private static <T> T pick(T primary, T stand, T def) {
        if (primary != null) return primary;
        if (stand != null) return stand;
        return def;
    }
}
