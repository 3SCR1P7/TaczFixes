package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.util.BulletTrackingFlag;
import com.ssscript.taczfixes.util.GunsmithLibHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class MixinTrackingDisableOnPenetration {
    @Inject(method = "onHitEntity", at = @At("RETURN"), remap = false)
    private void taczfixes$disableTrackingOnPenetration(TacHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        if (!Config.DISABLE_TRACKING_AFTER_PENETRATION.get()) {
            return;
        }
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        if (!GunsmithLibHelper.isTracking(bullet)) {
            return;
        }
        GunsmithLibHelper.disableTracking(bullet);
        bullet.getEntityData().set(BulletTrackingFlag.TRACKING_DISABLED, true);
    }
}
