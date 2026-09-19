package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.ClientGunLightManager;
import com.tacz.guns.entity.EntityKineticBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 客户端子弹逐 tick: 沿线生成动态光照。 */
@Mixin(EntityKineticBullet.class)
public class MixinEntityKineticBulletClientLight {

    @Inject(method = "m_8119_", at = @At("TAIL"), remap = false)
    private void taczfixes$bulletLight(CallbackInfo callback) {
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        if (bullet.level().isClientSide) {
            ClientGunLightManager.bulletLight(bullet);
        }
    }
}
