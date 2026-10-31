package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.SuppressionEffect;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端子弹生成时位置同步早于 spawn 数据(枪械 id/归属), 因此生成瞬间无法判定是否为重型武器。
 * 在 readSpawnData 结束时(数据就绪的准确时刻)通知压制效果, 对已在射程内挂起的子弹用真实 gunId 判定。
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class MixinEntityKineticBulletSpawnData {
    @Inject(method = "readSpawnData", at = @At("TAIL"), remap = false)
    private void taczfixes$suppressionDataReady(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        SuppressionEffect.onBulletDataReady((EntityKineticBullet) (Object) this);
    }
}
