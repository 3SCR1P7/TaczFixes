package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.GunShieldHelper;
import mod.chloeprime.gunsmithlib.common.gunpack_extension.shared.shield.ShieldBehavior;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShieldBehavior.class, remap = false)
public class MixinShieldBehavior {
    @Inject(method = "canBlockVanillaDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$gateVanilla(LivingEntity user, Vec3 sourcePos, ItemStack weapon,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (GunShieldHelper.isShieldUnavailable(user)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canBlockBulletDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$gateBullet(LivingEntity user, Vec3 bulletPos, ItemStack weapon,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (GunShieldHelper.isShieldUnavailable(user)) {
            cir.setReturnValue(false);
        }
    }
}
