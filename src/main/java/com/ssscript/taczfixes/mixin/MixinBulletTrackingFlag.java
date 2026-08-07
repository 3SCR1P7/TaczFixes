package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.BulletTrackingFlag;
import com.tacz.guns.entity.EntityKineticBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为 TACZ 子弹注册一个同步布尔标记（taczfixes:tracking_disabled）。
 * TACZ 的 defineSynchedData 是空实现，因此这是子弹唯一的同步数据参数。
 * 服务器在子弹穿透实体后置位该标记，客户端借此得知追踪已失效。
 */
@Mixin(EntityKineticBullet.class)
public class MixinBulletTrackingFlag {
    @Inject(method = "defineSynchedData", at = @At("HEAD"))
    private void taczfixes$defineTrackingFlag(CallbackInfo ci) {
        ((EntityKineticBullet) (Object) this).getEntityData().define(BulletTrackingFlag.TRACKING_DISABLED, false);
    }
}
