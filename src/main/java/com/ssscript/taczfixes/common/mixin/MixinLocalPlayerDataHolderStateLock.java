package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * 开火后客户端状态锁(clientStateLock)有固定 250ms 的最长等待窗口(tickStateLock 的 maxLockTime),
 * 该窗口与射击间隔无关, 是栓动枪"开火后延迟一小段时间才拉栓"的真正来源。
 * 对 MANUAL_ACTION 枪械按配件 manual_action_time 倍率缩放该窗口; 倍率 ≤ 0 时立即释放,
 * 其余同步门(换弹/切枪/拉栓中/近战冷却)保持原版判定不变。
 */
@Mixin(LocalPlayerDataHolder.class)
public class MixinLocalPlayerDataHolderStateLock {
    @Shadow(remap = false)
    @Final
    private LocalPlayer player;

    @Shadow(remap = false)
    public volatile boolean clientStateLock;

    @Shadow(remap = false)
    @Nullable
    public Predicate<IGunOperator> lockedCondition;

    @Shadow(remap = false)
    public long lockTimestamp;

    @Inject(method = "tickStateLock", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$scaledLockRelease(CallbackInfo ci) {
        if (!this.clientStateLock) {
            return;
        }
        ItemStack gun = this.player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return;
        }
        Bolt boltType = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getBolt())
                .orElse(null);
        if (boltType != Bolt.MANUAL_ACTION) {
            return;
        }
        double factor = AttachmentTaczFixesManager.getManualActionTimeFactor(gun);
        if (factor >= 1.0d) {
            return;
        }
        IGunOperator operator = IGunOperator.fromLivingEntity(this.player);
        long maxLockTime = factor <= 0.0d ? 0L : (long) (250.0d * factor);
        long lockTime = System.currentTimeMillis() - this.lockTimestamp;
        if (lockTime < maxLockTime && this.lockedCondition != null && !this.lockedCondition.test(operator)) {
            ci.cancel();
            return;
        }
        this.lockedCondition = null;
        if (operator.getSynReloadState().getStateType().isReloading()) {
            ci.cancel();
            return;
        }
        if (operator.getSynShootCoolDown() > 0) {
            ci.cancel();
            return;
        }
        if (operator.getSynDrawCoolDown() > 0) {
            ci.cancel();
            return;
        }
        if (operator.getSynIsBolting()) {
            ci.cancel();
            return;
        }
        if (operator.getSynMeleeCoolDown() > 0) {
            ci.cancel();
            return;
        }
        this.clientStateLock = false;
        ci.cancel();
    }
}
