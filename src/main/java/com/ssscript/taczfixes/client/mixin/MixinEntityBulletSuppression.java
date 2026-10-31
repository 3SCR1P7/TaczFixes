package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.SuppressionEffect;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 所有实体的位置变化最终都经过 Entity.setPosRaw(setPos/moveTo/同步包), 在此捕获子弹的位置更新,
 * 用“上一位置 → 新位置”的精确线段做压制检测: 客户端子弹自身不移动, 位置完全由服务端同步驱动。
 * 类型判断放在注入方法内联完成, 非子弹实体只多一次 instanceof, 不进入额外的方法调用。
 */
@Mixin(value = Entity.class, remap = false)
public abstract class MixinEntityBulletSuppression {
    @Inject(method = "setPosRaw", at = @At("HEAD"), remap = false)
    private void taczfixes$suppressionBulletMoved(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof EntityKineticBullet bullet) {
            SuppressionEffect.onBulletPositionSet(bullet, x, y, z);
        }
    }
}
