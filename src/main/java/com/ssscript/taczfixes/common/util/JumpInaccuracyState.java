package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JumpInaccuracyState {
    private static final Map<UUID, Entry> STATES = new ConcurrentHashMap<>();

    private static class Entry {
        final WeakReference<LivingEntity> shooter;
        ResourceLocation dataId;
        double factor = 1.0;

        Entry(LivingEntity shooter) {
            this.shooter = new WeakReference<>(shooter);
        }
    }

    private JumpInaccuracyState() {
    }

    /** 每tick为所有记录在案的射手更新倍率。滞空时向multiplier逼近，落地后向1逼近。multiplier<1时方向相反。 */
    public static void tick() {
        for (Map.Entry<UUID, Entry> pair : STATES.entrySet()) {
            Entry entry = pair.getValue();
            LivingEntity shooter = entry.shooter.get();
            if (shooter == null || !shooter.isAlive()) {
                STATES.remove(pair.getKey());
                continue;
            }
            ItemStack gunItem = shooter.getMainHandItem();
            ResourceLocation heldDataId = resolveDataId(gunItem);
            ResourceLocation dataId = heldDataId != null ? heldDataId : entry.dataId;
            if (dataId == null) continue;
            GunTaczFixesData.JumpInaccuracyConfig cfg = resolveConfig(dataId, gunItem);
            if (!isValid(cfg)) continue;
            entry.dataId = dataId;
            double target = shooter.onGround() ? 1.0 : cfg.multiplier;
            if (entry.factor < target) {
                entry.factor = Math.min(target, entry.factor + cfg.speed);
            } else if (entry.factor > target) {
                entry.factor = Math.max(target, entry.factor - cfg.speed);
            }
        }
    }

    /** 开火时应用当前滞空散布倍率，并记录射手与该枪械的关联。 */
    public static float apply(ResourceLocation dataId, ItemStack gunItem, LivingEntity shooter, float inaccuracy) {
        if (shooter == null || dataId == null) return inaccuracy;
        GunTaczFixesData.JumpInaccuracyConfig cfg = resolveConfig(dataId, gunItem);
        if (!isValid(cfg)) return inaccuracy;
        Entry entry = STATES.computeIfAbsent(shooter.getUUID(), k -> new Entry(shooter));
        entry.dataId = dataId;
        double lower = Math.min(1.0, cfg.multiplier);
        double upper = Math.max(1.0, cfg.multiplier);
        double factor = Math.min(Math.max(entry.factor, lower), upper);
        return (float) (inaccuracy * factor);
    }

    private static GunTaczFixesData.JumpInaccuracyConfig resolveConfig(ResourceLocation dataId, ItemStack gunItem) {
        GunTaczFixesData.JumpInaccuracyConfig cfg = TaczFixesDataManager.getJumpInaccuracyConfig(dataId);
        return AttachmentTaczFixesManager.adjustJumpInaccuracy(gunItem, cfg);
    }

    private static boolean isValid(GunTaczFixesData.JumpInaccuracyConfig cfg) {
        return cfg != null && cfg.multiplier != null && cfg.speed != null
                && cfg.multiplier > 0.0 && cfg.multiplier != 1.0 && cfg.speed > 0.0;
    }

    private static ResourceLocation resolveDataId(ItemStack gunItem) {
        if (gunItem == null || gunItem.isEmpty()) return null;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return null;
        ResourceLocation gunId = gun.getGunId(gunItem);
        return gunId == null ? null : TaczFixesDataManager.resolveDataId(gunId);
    }
}
