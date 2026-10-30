package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class LimbDamageFactorHelper {
    private LimbDamageFactorHelper() {
    }

    public static float getDefaultFactor() {
        return clamp(Config.LIMB_FACTOR_DEFAULT.get());
    }

    public static float getBaseFactor(ItemStack gunItem) {
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) {
            return getDefaultFactor();
        }
        return getBaseFactor(gun.getGunId(gunItem));
    }

    public static float getBaseFactor(ResourceLocation gunId) {
        if (gunId == null) {
            return getDefaultFactor();
        }
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        Double configured = TaczFixesDataManager.getLimbFactor(dataId);
        if (configured != null && Double.isFinite(configured)) {
            return (float) configured.doubleValue();
        }
        Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(gunId);
        if (gunIndex.isEmpty()) {
            return getDefaultFactor();
        }
        String type = gunIndex.get().getType();
        if (type == null) {
            return getDefaultFactor();
        }
        return (float) switch (type) {
            case "pistol" -> Config.GUN_TYPE_PISTOL.get().doubleValue();
            case "rifle" -> Config.GUN_TYPE_RIFLE.get().doubleValue();
            case "sniper" -> Config.GUN_TYPE_SNIPER.get().doubleValue();
            case "shotgun" -> Config.GUN_TYPE_SHOTGUN.get().doubleValue();
            case "smg" -> Config.GUN_TYPE_SMG.get().doubleValue();
            case "rpg" -> Config.GUN_TYPE_RPG.get().doubleValue();
            case "mg" -> Config.GUN_TYPE_MG.get().doubleValue();
            case "dmr" -> Config.GUN_TYPE_DMR.get().doubleValue();
            default -> Config.GUN_TYPE_OTHER.get().doubleValue();
        };
    }

    public static float getFinalFactor(ItemStack gunItem) {
        float base = getBaseFactor(gunItem);
        return clamp(AttachmentTaczFixesManager.applyLimbFactor(gunItem, base));
    }

    public static float clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0F;
        }
        return (float) Math.min(1.0D, Math.max(0.0D, value));
    }
}
