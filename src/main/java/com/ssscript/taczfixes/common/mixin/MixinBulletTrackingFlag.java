package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.BulletTrackingFlag;
import com.tacz.guns.entity.EntityKineticBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class MixinBulletTrackingFlag {
    @Inject(method = "m_8097_", at = @At("HEAD"), remap = false)
    private void taczfixes$defineTrackingFlag(CallbackInfo ci) {
        ((EntityKineticBullet) (Object) this).getEntityData().define(BulletTrackingFlag.TRACKING_DISABLED, false);
    }
}
