package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GunData.class)
public class MixinGunDataShootInterval {
    @Inject(method = "getShootInterval", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$efficiencyShootInterval(LivingEntity shooter, FireMode fireMode, ItemStack gun, CallbackInfoReturnable<Long> cir) {
        long interval = cir.getReturnValue();
        if (interval <= 0 || !GunEnchantmentHelper.isEnabled()) {
            return;
        }
        float factor = GunEnchantmentHelper.getEfficiencyFireRateFactor(gun);
        if (factor > 1.0f) {
            cir.setReturnValue(Math.max((long) (interval / factor), 1));
        }
    }
}