package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.BulletTrackingFlag;
import com.ssscript.taczfixes.common.util.GunsmithLibHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class MixinClientTrackingSync {
    @Inject(method = "tick", at = @At("HEAD"))
    private void taczfixes$stopClientTracking(CallbackInfo ci) {
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        if (!bullet.level().isClientSide()) {
            return;
        }
        if (!bullet.getEntityData().get(BulletTrackingFlag.TRACKING_DISABLED)) {
            return;
        }
        bullet.getPersistentData().putBoolean(GunsmithLibHelper.TRACKING_ENABLED_KEY, false);
    }
}
