package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.register.TaczFixesMod;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.ReloadExtraTracker;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 枪械 data 字段 bullet_in_barrel: 换弹装填阶段完成时, 额外装填 min(x, n) 发子弹。
 * x 为换弹前的弹药量, n 为字段值, 可为负数(负数即减少)。仅服务端应用。
 */
@Mixin(LivingEntityReload.class)
public class MixinLivingEntityReloadExtraAmmo {
    @Shadow(remap = false)
    @Final
    private LivingEntity shooter;

    @Shadow(remap = false)
    @Final
    private ShooterDataHolder data;

    @Unique
    private ReloadState.StateType taczfixes$oldState;

    /** 换弹开始: 记录换弹前弹药量。 */
    @Inject(method = "reload", at = @At("TAIL"), remap = false)
    private void taczfixes$capturePreReloadAmmo(CallbackInfo ci) {
        if (!this.data.reloadStateType.isReloading() || this.data.currentGunItem == null) {
            return;
        }
        ItemStack gun = this.data.currentGunItem.get();
        IGun iGun = gun == null ? null : IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return;
        }
        ReloadExtraTracker.capture(this.shooter.getUUID(), iGun.getCurrentAmmoCount(gun));
    }

    @Inject(method = "tickReloadState", at = @At("HEAD"), remap = false)
    private void taczfixes$snapshotOldState(CallbackInfoReturnable<ReloadState> cir) {
        this.taczfixes$oldState = this.data.reloadStateType;
    }

    @Inject(method = "tickReloadState", at = @At("RETURN"), remap = false)
    private void taczfixes$applyExtraAmmo(CallbackInfoReturnable<ReloadState> cir) {
        if (this.shooter.level().isClientSide()) {
            return;
        }
        // 仅在 装填 -> 收尾/结束 的变化点应用一次
        if (this.taczfixes$oldState != ReloadState.StateType.EMPTY_RELOAD_FEEDING
                && this.taczfixes$oldState != ReloadState.StateType.TACTICAL_RELOAD_FEEDING) {
            return;
        }
        ReloadState.StateType newState = cir.getReturnValue().getStateType();
        if (newState == this.taczfixes$oldState) {
            return;
        }
        if (this.data.currentGunItem == null) {
            return;
        }
        ItemStack gun = this.data.currentGunItem.get();
        IGun iGun = gun == null ? null : IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return;
        }
        Integer n = TaczFixesDataManager.getBulletInBarrel(gun);
        if (n == null || n == 0) {
            return;
        }
        if (ReloadExtraTracker.isApplied(this.shooter.getUUID())) {
            return;
        }
        ReloadExtraTracker.markApplied(this.shooter.getUUID());
        int extra = Math.min(ReloadExtraTracker.getPreAmmo(this.shooter.getUUID()), n);
        if (extra == 0) {
            return;
        }
        int updated = Math.max(0, iGun.getCurrentAmmoCount(gun) + extra);
        iGun.setCurrentAmmoCount(gun, updated);
        TaczFixesMod.LOGGER.debug("taczfixes: bullet_in_barrel applied +{} for {}", extra,
                iGun.getGunId(gun));
    }
}
