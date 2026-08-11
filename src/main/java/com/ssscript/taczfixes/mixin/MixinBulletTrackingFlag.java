package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.BulletTrackingFlag;
import com.tacz.guns.entity.EntityKineticBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class MixinBulletTrackingFlag {
    @Inject(method = "defineSynchedData", at = @At("HEAD"))
    private void taczfixes$defineTrackingFlag(CallbackInfo ci) {
        ((EntityKineticBullet) (Object) this).getEntityData().define(BulletTrackingFlag.TRACKING_DISABLED, false);
    }
}
