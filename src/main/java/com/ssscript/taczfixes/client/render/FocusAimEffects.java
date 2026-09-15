package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.minecraft.client.player.LocalPlayer;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/FocusAimEffects.class */
public final class FocusAimEffects {
    private FocusAimEffects() {
    }

    public static float applyAdditionalMainhandRecoil(LocalPlayer player, float originalModifier, float dualScaledModifier) {
        if (player == null || !DualFocusAimState.isActive() || !IClientPlayerGunOperator.fromLocalPlayer(player).isAim()) {
            return dualScaledModifier;
        }
        double result = dualScaledModifier + (originalModifier * 0.5d);
        if (!Double.isFinite(result) || Math.abs(result) > 3.4028234663852886E38d) {
            return dualScaledModifier;
        }
        return (float) result;
    }
}
