package com.ssscript.taczfixes.common.data;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.ssscript.taczfixes.common.util.RecoilMultiplierResolver;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class AttachmentTaczFixesManager {
    private static final Map<ResourceLocation, AttachmentTaczFixesData> DATA = new ConcurrentHashMap<>();

    private AttachmentTaczFixesManager() {
    }

    public static void putAll(Map<ResourceLocation, AttachmentTaczFixesData> map) {
        DATA.clear();
        DATA.putAll(map);
    }

    public static AttachmentTaczFixesData get(ResourceLocation dataId) {
        return dataId == null ? null : DATA.get(dataId);
    }

    public static double applyLimbFactor(ItemStack gunItem, double baseLimbFactor) {
        List<Modifier> modifiers = collectValues(gunItem, data -> data.limb_factor);
        if (modifiers.isEmpty()) {
            return baseLimbFactor;
        }
        return AttachmentPropertyManager.eval(modifiers, baseLimbFactor);
    }

    public static List<GunTaczFixesData.RecoilConfig> resolveRecoilList(ItemStack gunItem, FireMode mode) {
        String key = switch (mode) {
            case AUTO -> "auto";
            case SEMI -> "semi";
            case BURST -> "burst";
            case UNKNOWN -> null;
        };
        if (key == null) return null;
        List<GunTaczFixesData.RecoilConfig> result = new ArrayList<>();
        for (AttachmentTaczFixesData data : collectData(gunItem)) {
            if (data.recoil_multiplier == null) continue;
            GunTaczFixesData.RecoilConfig config = data.recoil_multiplier.get(key);
            if (config != null && RecoilMultiplierResolver.isActive(config)) {
                result.add(config);
            }
        }
        return result.isEmpty() ? null : result;
    }

    public static float applyFireKnockback(ItemStack gunItem, float force) {
        List<Modifier> modifiers = collectValues(gunItem, data -> data.fire_knockback_power);
        if (modifiers.isEmpty()) {
            return force;
        }
        return (float) AttachmentPropertyManager.eval(modifiers, force);
    }

    public static InaccuracyParams adjustInaccuracy(ItemStack gunItem, InaccuracyParams base) {
        if (gunItem == null || base == null) return base;
        List<AttachmentTaczFixesData.InaccuracyAdjust> adjustments =
                collectValues(gunItem, data -> data.inaccuracy_multiplier);
        if (adjustments.isEmpty()) {
            return base;
        }
        List<Modifier> maxStackMods = new ArrayList<>();
        List<Modifier> perShotMods = new ArrayList<>();
        List<Modifier> speedMods = new ArrayList<>();
        List<Modifier> delayMods = new ArrayList<>();
        for (AttachmentTaczFixesData.InaccuracyAdjust adj : adjustments) {
            if (adj.max_stack != null) maxStackMods.add(adj.max_stack);
            if (adj.per_shot != null) perShotMods.add(adj.per_shot);
            if (adj.cooldown_speed != null) speedMods.add(adj.cooldown_speed);
            if (adj.cooldown_delay != null) delayMods.add(adj.cooldown_delay);
        }
        double maxStack = maxStackMods.isEmpty() ? base.maxStack
                : AttachmentPropertyManager.eval(maxStackMods, base.maxStack);
        double delay = delayMods.isEmpty() ? base.cooldownDelay
                : AttachmentPropertyManager.eval(delayMods, base.cooldownDelay);
        double speed = speedMods.isEmpty() ? base.cooldownSpeed
                : AttachmentPropertyManager.eval(speedMods, base.cooldownSpeed);
        double shotPercent = perShotMods.isEmpty() ? base.shotPercent
                : AttachmentPropertyManager.eval(perShotMods, base.shotPercent);
        double shotAddend = perShotMods.isEmpty() ? base.shotAddend
                : AttachmentPropertyManager.eval(perShotMods, base.shotAddend);
        return new InaccuracyParams(
                Math.max(0, (int) Math.round(maxStack)),
                Math.max(0, (long) Math.round(delay)),
                Math.max(0.0, speed),
                Math.max(0.0, shotPercent),
                shotAddend);
    }

    private static <T> List<T> collectValues(ItemStack gunItem, Function<AttachmentTaczFixesData, T> getter) {
        List<T> result = new ArrayList<>();
        for (AttachmentTaczFixesData data : collectData(gunItem)) {
            T value = getter.apply(data);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<AttachmentTaczFixesData> collectData(ItemStack gunItem) {
        List<AttachmentTaczFixesData> result = new ArrayList<>();
        if (gunItem == null) return result;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return result;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation id = gun.getAttachmentId(gunItem, type);
            if (DefaultAssets.isEmptyAttachmentId(id)) continue;
            AttachmentTaczFixesData data = resolveData(id);
            if (data != null) {
                result.add(data);
            }
        }
        return result;
    }

    private static AttachmentTaczFixesData resolveData(ResourceLocation attachmentId) {
        AttachmentTaczFixesData data = get(attachmentId);
        if (data != null) return data;
        ResourceLocation dataId = TimelessAPI.getCommonAttachmentIndex(attachmentId)
                .map(index -> index.getPojo().getData())
                .orElse(attachmentId);
        return get(dataId);
    }
}
