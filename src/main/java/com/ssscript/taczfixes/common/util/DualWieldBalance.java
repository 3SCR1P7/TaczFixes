package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import java.util.Locale;
import net.minecraft.world.item.ItemStack;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/util/DualWieldBalance.class */
public final class DualWieldBalance {
    public static final double OTHER_GUN_RECOIL_MULTIPLIER = 2.0d;
    public static final double HEAVY_GUN_RECOIL_MULTIPLIER = 2.5d;
    public static final double HEAVY_GUN_SPEED_PENALTY = 0.07d;
    public static final double HIP_FIRE_INACCURACY_MULTIPLIER = 1.5d;
    public static final double FOCUS_AIM_RECOIL_BONUS = 0.5d;
    public static final double FOCUS_AIM_INACCURACY_MULTIPLIER = 1.15d;
    public static final double RELOAD_TIME_SCALE = 0.5d;

    private DualWieldBalance() {
    }

    public static double getReloadTimeScale(ItemStack stack) {
        IGun gun;
        if (stack == null || stack.isEmpty() || (gun = IGun.getIGunOrNull(stack)) == null) {
            return 0.5d;
        }
        String gunType = (String) TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> {
            return normalizeType(index.getType());
        }).orElse("");
        return "pistol".equals(gunType) ? 1.0d : 0.5d;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String normalizeType(String gunType) {
        return gunType == null ? "" : gunType.trim().toLowerCase(Locale.ROOT);
    }
}
