package com.example.taczfixes.mixin;

import com.example.taczfixes.util.BurstBlockHelper;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(LivingEntityShoot.class)
public class LivingEntityShootMixin {
    @Shadow(remap = false)
    private ShooterDataHolder data;

    @Inject(method = "shoot", at = @At("HEAD"), cancellable = true, remap = false)
    private void onBurstRestricted(CallbackInfoReturnable<ShootResult> cir) {
        if (data.currentGunItem == null) return;
        ItemStack gunItem = data.currentGunItem.get();
        if (!(gunItem.getItem() instanceof IGun iGun)) return;
        if (iGun.getFireMode(gunItem) != FireMode.BURST) return;
        if (!BurstBlockHelper.hasRestrictedAttachment(iGun, gunItem)) return;
        cir.setReturnValue(ShootResult.NO_AMMO);
    }
}
