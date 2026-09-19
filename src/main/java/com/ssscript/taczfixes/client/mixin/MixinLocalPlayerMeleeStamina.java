package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.DualWieldClient;
import com.ssscript.taczfixes.client.util.AimingStaminaClientState;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 上肢耐力不足时客户端直接拦截近战(不播放近战动作); 副手近战冷却中主手也不能近战。 */
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerMelee", remap = false)
public class MixinLocalPlayerMeleeStamina {

    @Inject(method = "melee", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$blockLowAimingStamina(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (DualWieldClient.isDualMode(player) && DualWieldClient.offhandMeleeBlocksMainHand()) {
            ci.cancel();
            return;
        }
        GunTaczFixesData.AimingStaminaConfig cfg =
                AttachmentTaczFixesManager.resolveAimingStamina(player.getMainHandItem());
        float cost = cfg.melee_cost.floatValue();
        if (cost > 0f && AimingStaminaClientState.isInsufficient(cost)) {
            ci.cancel();
        }
    }
}
