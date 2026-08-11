package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public class MixinEnchantmentGunCompatible {
    @Inject(method = "isCompatibleWith", at = @At("HEAD"), cancellable = true)
    private void taczfixes$allowGunConflicts(Enchantment other, CallbackInfoReturnable<Boolean> cir) {
        if (GunEnchantmentHelper.isGunEnchanting()) {
            cir.setReturnValue(true);
        }
    }
}
