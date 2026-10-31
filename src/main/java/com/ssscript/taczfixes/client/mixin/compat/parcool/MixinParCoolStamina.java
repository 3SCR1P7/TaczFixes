package com.ssscript.taczfixes.client.mixin.compat.parcool;

import com.alrex.parcool.common.attachment.common.ReadonlyStamina;
import com.ssscript.taczfixes.client.util.ParCoolStaminaConsumeHelper;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ParCool 3.4.3.3 stamina handlers: redirect consumption to our stamina. */
@Mixin(targets = "com.alrex.parcool.common.stamina.handlers.ParCoolStaminaHandler", remap = false)
public class MixinParCoolStamina {
    @Inject(method = "consume", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$consumeOurStamina(LocalPlayer player, ReadonlyStamina stamina,
                                             int amount, CallbackInfoReturnable<ReadonlyStamina> cir) {
        if (ParCoolStaminaConsumeHelper.handle(player, amount)) {
            cir.setReturnValue(stamina);
        }
    }
}
