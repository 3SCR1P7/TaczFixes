package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.BulletTickHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 子弹逐 tick 的 Lua 回调: 枪械发射的每颗子弹每 tick(含首 tick)执行该枪脚本中的 M.tick_bullet(api)。
 * 每一颗子弹独立执行(api 绑定当前子弹)。服务端执行, 修改经同步包实时反映到客户端曳光弹。
 */
@Mixin(EntityKineticBullet.class)
public abstract class MixinEntityKineticBulletScript {

    @Inject(method = "onBulletTick", at = @At("HEAD"), remap = false)
    private void taczfixes$invokeBulletTick(CallbackInfo ci) {
        BulletTickHelper.runBulletTick((EntityKineticBullet) (Object) this);
    }

}
