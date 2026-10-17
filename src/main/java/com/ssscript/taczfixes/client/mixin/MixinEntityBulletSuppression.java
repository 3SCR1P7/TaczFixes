package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.SuppressionEffect;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 所有实体的位置变化最终都经过 Entity.m_20343_(setPos/moveTo/同步包), 在此捕获子弹的位置更新,
 * 用“上一位置 → 新位置”的精确线段做压制检测: 客户端子弹自身不移动, 位置完全由服务端同步驱动。
 */
@Mixin(value = Entity.class, remap = false)
public abstract class MixinEntityBulletSuppression {
    @Inject(method = "m_20343_", at = @At("HEAD"), remap = false)
    private void taczfixes$suppressionBulletMoved(double x, double y, double z, CallbackInfo ci) {
        SuppressionEffect.onEntityPositionSet((Entity) (Object) this, x, y, z);
    }
}
