package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.ParCoolStaminaConsumeHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ParCool 耐力消耗改为消耗本模组耐力(ParCoolStaminaHunger)。 */
@Mixin(targets = "com.alrex.parcool.common.capability.stamina.HungerStamina", remap = false)
public class MixinParCoolStaminaHunger {
    @Shadow(remap = false)
    private Player player;

    @Inject(method = "consume", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$consumeOurStamina(int amount, CallbackInfo ci) {
        if (ParCoolStaminaConsumeHelper.handle(player, amount)) {
            ci.cancel();
        }
    }
}