package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.AimingStaminaClientState;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.tacz.guns.api.entity.ShootResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 上肢耐力不足时客户端直接拦截开火(不播放开火动作)。 */
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerShoot", remap = false)
public class MixinLocalPlayerShootStamina {

    @Inject(method = "shoot", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$blockLowAimingStamina(CallbackInfoReturnable<ShootResult> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        GunTaczFixesData.AimingStaminaConfig cfg =
                AttachmentTaczFixesManager.resolveAimingStamina(player.getMainHandItem());
        float cost = cfg.shoot_cost.floatValue();
        if (cost > 0f && AimingStaminaClientState.isInsufficient(cost)) {
            cir.setReturnValue(ShootResult.COOL_DOWN);
        }
    }
}
