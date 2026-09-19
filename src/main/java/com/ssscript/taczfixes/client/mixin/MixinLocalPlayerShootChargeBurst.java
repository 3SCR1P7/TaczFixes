package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.util.ChargeStorage;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** charge: 客户端连发预测按剩余电量截断; 电量不足的那一发不播开火动画/音效, 改为在原本该开火的时刻播放 dry_fire。 */
@Mixin(LocalPlayerShoot.class)
public class MixinLocalPlayerShootChargeBurst {

    @Unique
    private static volatile boolean taczfixes$burstDryFirePending = false;

    @Redirect(method = "doShoot", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), remap = false)
    private int taczfixes$capBurstByCharge(int ammoCount, int fireModeCount) {
        int count = Math.min(ammoCount, fireModeCount);
        taczfixes$burstDryFirePending = false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return count;
        }
        ItemStack stack = player.getMainHandItem();
        GunTaczFixesData.ChargeConfig cfg = ChargeStorage.config(stack);
        if (cfg == null || !Boolean.TRUE.equals(cfg.blocking_fire) || cfg.fire_consumption == null
                || cfg.fire_consumption.intValue() <= 0 || ChargeStorage.getMax(stack) <= 0) {
            return count;
        }
        int affordable = ChargeStorage.get(stack) / cfg.fire_consumption.intValue();
        if (affordable >= count) {
            return count;
        }
        if (affordable <= 0) {
            return 0;
        }
        taczfixes$burstDryFirePending = true;
        return affordable;
    }

    @Inject(method = "lambda$doShoot$2(Ljava/util/concurrent/atomic/AtomicInteger;Lcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/api/item/IGun;Lnet/minecraft/world/item/ItemStack;IFLcom/tacz/guns/client/resource/GunDisplayInstance;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicInteger;getAndIncrement()I", shift = At.Shift.BEFORE),
            remap = false)
    private void taczfixes$dryFireAfterLastBurstRound(AtomicInteger counter, GunData gunData, IGun gun, ItemStack stack,
                                                      int count, float chargeProgress, GunDisplayInstance display,
                                                      CallbackInfo ci) {
        if (!taczfixes$burstDryFirePending || counter.get() + 1 < count) {
            return;
        }
        taczfixes$burstDryFirePending = false;
        long delay = Math.max(1L, gunData.getBurstShootInterval());
        LocalPlayerDataHolder.SCHEDULED_EXECUTOR_SERVICE.schedule(() -> Minecraft.getInstance().execute(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                SoundPlayManager.playDryFireSound(player, display);
            }
        }), delay, TimeUnit.MILLISECONDS);
    }
}
