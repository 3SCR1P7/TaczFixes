package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 栓动枪械开火后需等待射击间隔冷却结束才能开始拉栓。
 * 对 MANUAL_ACTION 枪械, 将该剩余冷却按配件 manual_action_time 倍率同步缩减;
 * 倍率 ≤ 0 时冷却直接归零(立即拉栓)。不影响射速: 再次开火仍受膛内有弹与拉栓完成限制。
 */
@Mixin(LivingEntityShoot.class)
public class MixinLivingEntityShootBoltCooldown {
    @Shadow(remap = false)
    @Final
    private ShooterDataHolder data;

    @Inject(method = "getShootCoolDown(J)J", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$scaleBoltPreCooldown(long timestamp, CallbackInfoReturnable<Long> cir) {
        long coolDown = cir.getReturnValue();
        if (coolDown <= 0) {
            return;
        }
        ItemStack gun = data.currentGunItem == null ? null : data.currentGunItem.get();
        if (gun == null || !(gun.getItem() instanceof AbstractGunItem iGun)) {
            return;
        }
        Bolt boltType = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getBolt())
                .orElse(null);
        if (boltType != Bolt.MANUAL_ACTION) {
            return;
        }
        double factor = AttachmentTaczFixesManager.getManualActionTimeFactor(gun);
        if (factor <= 0.0d) {
            cir.setReturnValue(0L);
            return;
        }
        if (factor == 1.0d) {
            return;
        }
        cir.setReturnValue(Math.max(0L, (long) (coolDown / factor)));
    }
}
