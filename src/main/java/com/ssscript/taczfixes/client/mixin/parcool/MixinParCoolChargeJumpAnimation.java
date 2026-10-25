package com.ssscript.taczfixes.client.mixin.parcool;

import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.client.animation.impl.JumpChargingAnimator;
import com.alrex.parcool.common.capability.Parkourability;
import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JumpChargingAnimator.class)
public abstract class MixinParCoolChargeJumpAnimation {

    @Inject(method = "animatePre", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$skipChargingPose(Player player, Parkourability parkourability,
                                            PlayerModelTransformer transformer,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (Config.DISABLE_PARCOOL_CHARGE_JUMP_ANIMATION.get() && holdingGun(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "rotatePre", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$skipChargingRotation(Player player, Parkourability parkourability,
                                               PlayerModelRotator rotator,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (Config.DISABLE_PARCOOL_CHARGE_JUMP_ANIMATION.get() && holdingGun(player)) {
            cir.setReturnValue(false);
        }
    }

    private static boolean holdingGun(Player player) {
        return player != null && (IGun.getIGunOrNull(player.getMainHandItem()) != null
                || IGun.getIGunOrNull(player.getOffhandItem()) != null);
    }
}
