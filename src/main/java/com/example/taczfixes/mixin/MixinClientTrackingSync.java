package com.example.taczfixes.mixin;

import com.example.taczfixes.util.BulletTrackingFlag;
import com.example.taczfixes.util.GunsmithLibHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端同步：当服务器标记子弹已失追踪（穿透实体后）时，
 * 清除客户端子弹副本上的追踪 NBT 开关，使客户端不再对子弹进行追踪转向。
 * GunsmithLib 客户端子弹的 NBT 无法自动同步，必须显式同步该状态。
 */
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
