package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
/* 双持后坐力倍率: 双持时统一使用配置倍率, 单手瞄准时使用瞄准倍率。 */
public final class DualRecoilMultiplier {
    private DualRecoilMultiplier() {
    }

    public static float apply(LocalPlayer player, float modifier) {
        if (!DualWieldEligibility.isDualWielding(player) || !Float.isFinite(modifier)) {
            return modifier;
        }
        ItemStack stack = player.getMainHandItem();
        boolean focusAim = DualFocusAimState.isActive() && IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
        double fallback = focusAim ? DualWieldEligibility.getClientFocusAimRecoilMultiplier() : DualWieldEligibility.getClientRecoilMultiplier();
        Double override = focusAim ? DualWieldOverrides.focusAimRecoilMultiplier(stack) : DualWieldOverrides.recoilMultiplier(stack);
        return scale(modifier, DualWieldOverrides.withFallback(override, fallback));
    }

    public static float applyOffhand(LocalPlayer player, float modifier) {
        if (!DualWieldEligibility.isDualWielding(player) || !Float.isFinite(modifier)) {
            return modifier;
        }
        ItemStack stack = player.getOffhandItem();
        double fallback = DualWieldEligibility.getClientRecoilMultiplier();
        return scale(modifier, DualWieldOverrides.withFallback(DualWieldOverrides.recoilMultiplier(stack), fallback));
    }

    private static float scale(float modifier, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 0.0d) {
            return modifier;
        }
        double scaled = modifier * multiplier;
        if (!Double.isFinite(scaled) || Math.abs(scaled) > 3.4028234663852886E38d) {
            return modifier;
        }
        return (float) scaled;
    }
}
