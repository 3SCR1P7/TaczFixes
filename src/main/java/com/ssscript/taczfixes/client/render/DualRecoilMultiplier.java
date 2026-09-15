package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.WeightModifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualRecoilMultiplier.class */
public final class DualRecoilMultiplier {
    private DualRecoilMultiplier() {
    }

    public static float apply(LocalPlayer player, float modifier) {
        AttachmentCacheProperty cacheProperty;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (player == null) {
            cacheProperty = null;
        } else {
            cacheProperty = IGunOperator.fromLivingEntity(player).getCacheProperty();
        }
        AttachmentCacheProperty cache = cacheProperty;
        float dualScaledModifier = apply(player, stack, cache, modifier);
        return FocusAimEffects.applyAdditionalMainhandRecoil(player, modifier, dualScaledModifier);
    }

    public static float apply(LocalPlayer player, ItemStack firedStack, AttachmentCacheProperty firedCache, float modifier) {
        if (!DualWieldEligibility.isDualWielding(player) || !Float.isFinite(modifier) || firedStack == null || firedStack.isEmpty()) {
            return modifier;
        }
        double multiplier = resolveMultiplier(firedStack, firedCache);
        if (!Double.isFinite(multiplier) || multiplier < 0.0d) {
            return modifier;
        }
        double scaled = modifier * multiplier;
        if (!Double.isFinite(scaled) || Math.abs(scaled) > 3.4028234663852886E38d) {
            return modifier;
        }
        return (float) scaled;
    }

    private static double resolveMultiplier(ItemStack stack, AttachmentCacheProperty cache) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return DualWieldEligibility.getClientRecoilMultiplier();
        }
        String gunType = (String) TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> {
            return normalizeType(index.getType());
        }).orElse("");
        if ("pistol".equals(gunType) || "smg".equals(gunType)) {
            return DualWieldEligibility.getClientRecoilMultiplier();
        }
        if (hasHeavyWeightPenalty(stack, cache)) {
            return 2.5d;
        }
        return 2.0d;
    }

    private static boolean hasHeavyWeightPenalty(ItemStack stack, AttachmentCacheProperty cache) {
        double weightFactor = ((Double) SyncConfig.WEIGHT_SPEED_MULTIPLIER.get()).doubleValue();
        if (!Double.isFinite(weightFactor) || weightFactor <= 0.0d) {
            return false;
        }
        Float cachedWeight = cache == null ? null : (Float) cache.getCache(WeightModifier.ID);
        double weight = cachedWeight == null ? resolveFallbackWeight(stack) : cachedWeight.floatValue();
        return Double.isFinite(weight) && weight > 0.0d && weight * weightFactor >= 0.07d;
    }

    private static double resolveFallbackWeight(ItemStack stack) {
        GunData gunData;
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null || (gunData = (GunData) TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> {
            return index.getGunData();
        }).orElse(null)) == null) {
            return Double.NaN;
        }
        return AttachmentDataUtils.getWightWithAttachment(stack, gunData);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String normalizeType(String gunType) {
        return gunType == null ? "" : gunType.trim().toLowerCase(Locale.ROOT);
    }
}
