package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityKineticBullet.class)
public class MixinEntityKineticBulletLevelDamage {
    @Inject(method = "getDamage", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$applyLevelAndRiptide(Vec3 hitPos, CallbackInfoReturnable<Float> cir) {
        Entity owner = ((EntityKineticBullet) (Object) this).getOwner();
        if (!(owner instanceof LivingEntity shooter)) {
            return;
        }
        float factor = 1.0f;
        if (GunEnchantmentHelper.isEnabled()) {
            factor *= GunEnchantmentHelper.getRiptideDamageFactor(shooter);
            factor *= com.ssscript.taczfixes.common.util.OverloadDamage.getFactor(shooter);
        }
        if (factor != 1.0f) {
            cir.setReturnValue(cir.getReturnValue() * factor);
        }
    }
}