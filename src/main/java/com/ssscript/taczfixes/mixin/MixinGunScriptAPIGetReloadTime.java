package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 快速装填：按附魔等级缩短换弹耗时。
 * getReloadTime 返回"经过的换弹时间"，这里按时间因子放大，
 * 使换弹进度判定（defaultTickReload）提前完成。换弹动画由客户端倍速播放匹配。
 */
@Mixin(ModernKineticGunScriptAPI.class)
public class MixinGunScriptAPIGetReloadTime {
    @Shadow(remap = false)
    private LivingEntity shooter;

    @Inject(method = "getReloadTime", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$scaleReloadTime(CallbackInfoReturnable<Long> cir) {
        long elapsed = cir.getReturnValue();
        if (elapsed <= 0) {
            return;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        float factor = GunEnchantmentHelper.getQuickChargeTimeFactor(shooter);
        if (factor < 1.0f) {
            cir.setReturnValue((long) (elapsed / factor));
        }
    }
}
