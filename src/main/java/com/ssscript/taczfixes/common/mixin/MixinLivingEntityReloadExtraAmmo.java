package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.ReloadExtraTracker;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
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
 *
 * <p>额外装填的子弹同样要从玩家背包里扣除: 走 TaCZ 自己的 {@code consumeAmmoFromPlayer},
 * 实际扣除多少就只装填多少, 创造模式/无限弹药等原版不消耗的情况保持一致。</p>
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

    // TaCZ 0929 uses its own GunTime clock; keep all timestamps in that time domain.

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
        ReloadExtraTracker.capture(this.shooter.getUUID(), this.data, iGun.getCurrentAmmoCount(gun));
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
        if (ReloadExtraTracker.isApplied(this.data)) {
            return;
        }
        ReloadExtraTracker.markApplied(this.data);
        int extra = Math.min(ReloadExtraTracker.getPreAmmo(this.data), n);
        if (extra > 0) {
            // 额外装填的子弹必须从背包/备用弹药里扣: 扣不到就不装, 避免白送子弹。
            extra = taczfixes$consumeExtraAmmo(gun, extra);
            if (extra <= 0) {
                return;
            }
        }
        if (extra == 0) {
            return;
        }
        int updated = Math.max(0, iGun.getCurrentAmmoCount(gun) + extra);
        iGun.setCurrentAmmoCount(gun, updated);
        TaczFixesMod.LOGGER.debug("taczfixes: bullet_in_barrel applied {} for {}", extra,
                iGun.getGunId(gun));
    }

    /**
     * 复刻原版换弹的弹药消耗, 返回实际可装填的数量。
     *
     * <p>只有逐发扣子弹的弹匣换弹({@code FeedType.MAGAZINE})才会为额外装填扣背包;
     * 燃料({@code FUEL})换弹按整次扣一个燃料、手动/背包直取类型在收尾阶段本来就不扣弹,
     * 这些类型额外装填的几发不再重复扣。创造模式/无限弹药与原版一致, 不消耗。</p>
     */
    @Unique
    private int taczfixes$consumeExtraAmmo(ItemStack gun, int amount) {
        try {
            ModernKineticGunScriptAPI api = new ModernKineticGunScriptAPI();
            api.setItemStack(gun);
            api.setShooter(this.shooter);
            api.setDataHolder(this.data);
            CommonGunIndex index = api.getGunIndex();
            GunData gunData = index == null ? null : index.getGunData();
            GunReloadData reloadData = gunData == null ? null : gunData.getReloadData();
            if (reloadData == null || reloadData.getType() != FeedType.MAGAZINE
                    || reloadData.isInfinite() || !api.isReloadingNeedConsumeAmmo()) {
                return amount;
            }
            return api.consumeAmmoFromPlayer(amount);
        } catch (Throwable throwable) {
            TaczFixesMod.LOGGER.debug("taczfixes: bullet_in_barrel ammo consumption failed", throwable);
            return 0;
        }
    }
}
