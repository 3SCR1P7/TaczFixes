package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModernKineticGunScriptAPI.class)
public class MixinGunScriptAPIGetBoltTime {
    @Shadow(remap = false)
    private LivingEntity shooter;

    @Inject(method = "getBoltTime", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$shortenBoltTime(CallbackInfoReturnable<Long> cir) {
        long elapsed = cir.getReturnValue();
        if (elapsed <= 0 || !GunEnchantmentHelper.isEnabled()) {
            return;
        }
        float factor = GunEnchantmentHelper.getEfficiencyBoltTimeFactor(GunEnchantmentHelper.getGunStack(shooter));
        if (factor < 1.0f) {
            cir.setReturnValue((long) (elapsed / factor));
        }
    }
}