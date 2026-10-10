package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.BurstBlockHelper;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayerShoot.class)
public class MixinLocalPlayerShootPreCheck {
    @Shadow(remap = false)
    private LocalPlayer player;

    @Inject(method = "preCheck", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void taczfixes$blockLowAimingStamina(IGun iGun, IGunOperator gunOperator, ClientGunIndex gunIndex,
                                                 ItemStack mainHandItem, GunDisplayInstance display, GunData gunData,
                                                 boolean playDrySound, CallbackInfoReturnable<ShootResult> cir) {
        com.ssscript.taczfixes.common.data.GunTaczFixesData.AimingStaminaConfig cfg =
                com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager.resolveAimingStamina(mainHandItem);
        float cost = cfg.shoot_cost.floatValue();
        if (cost > 0f && com.ssscript.taczfixes.client.util.AimingStaminaClientState.isInsufficient(cost)) {
            cir.setReturnValue(ShootResult.COOL_DOWN);
        }
    }

    /** 连发配件限制: 先于其它 dry_fire 判定, 被其拦截时不播放 dry_fire。 */
    @Inject(method = "preCheck", at = @At("TAIL"), cancellable = true, remap = false, require = 0)
    private void onPreCheckTail(IGun iGun, IGunOperator gunOperator, ClientGunIndex gunIndex,
                                ItemStack mainHandItem, GunDisplayInstance display, GunData gunData,
                                boolean playDrySound, CallbackInfoReturnable<ShootResult> cir) {
        if (cir.getReturnValue() != null) return;
        if (iGun.getFireMode(mainHandItem) != FireMode.BURST) return;
        if (!BurstBlockHelper.hasRestrictedAttachment(iGun, mainHandItem)) return;
        cir.setReturnValue(ShootResult.NO_AMMO);
    }

    /** 仅当没有其它拦截原因(换弹/拉栓/冷却等)时才播放 dry_fire。 */
    @Inject(method = "preCheck", at = @At("TAIL"), cancellable = true, remap = false, require = 0)
    private void taczfixes$blockUnderwater(IGun iGun, IGunOperator gunOperator, ClientGunIndex gunIndex,
                                           ItemStack mainHandItem, GunDisplayInstance display, GunData gunData,
                                           boolean playDrySound, CallbackInfoReturnable<ShootResult> cir) {
        if (cir.getReturnValue() != null) return;
        if (com.ssscript.taczfixes.common.util.UnderwaterShooting.isBlocked(player, mainHandItem)) {
            if (playDrySound) {
                SoundPlayManager.playDryFireSound(player, display);
            }
            cir.setReturnValue(ShootResult.NO_AMMO);
        }
    }

    @Inject(method = "preCheck", at = @At("TAIL"), cancellable = true, remap = false, require = 0)
    private void taczfixes$blockTooClose(IGun iGun, IGunOperator gunOperator, ClientGunIndex gunIndex,
                                         ItemStack mainHandItem, GunDisplayInstance display, GunData gunData,
                                         boolean playDrySound, CallbackInfoReturnable<ShootResult> cir) {
        if (cir.getReturnValue() != null) return;
        if (com.ssscript.taczfixes.common.util.GunBlocking.isFireDisabled(player, mainHandItem)) {
            if (playDrySound) {
                SoundPlayManager.playDryFireSound(player, display);
            }
            cir.setReturnValue(ShootResult.NO_AMMO);
        }
    }

    @Inject(method = "preCheck", at = @At("TAIL"), cancellable = true, remap = false, require = 0)
    private void taczfixes$blockLowCharge(IGun iGun, IGunOperator gunOperator, ClientGunIndex gunIndex,
                                          ItemStack mainHandItem, GunDisplayInstance display, GunData gunData,
                                          boolean playDrySound, CallbackInfoReturnable<ShootResult> cir) {
        if (cir.getReturnValue() != null) return;
        com.ssscript.taczfixes.common.data.GunTaczFixesData.ChargeConfig chargeCfg =
                com.ssscript.taczfixes.common.util.ChargeStorage.config(mainHandItem);
        if (chargeCfg == null || !Boolean.TRUE.equals(chargeCfg.blocking_fire)
                || chargeCfg.fire_consumption == null || chargeCfg.fire_consumption.intValue() <= 0) {
            return;
        }
        if (com.ssscript.taczfixes.common.util.ChargeStorage.getMax(mainHandItem) <= 0) {
            return;
        }
        if (com.ssscript.taczfixes.common.util.ChargeStorage.get(mainHandItem) < chargeCfg.fire_consumption.intValue()) {
            if (playDrySound) {
                SoundPlayManager.playDryFireSound(player, display);
            }
            cir.setReturnValue(ShootResult.NO_AMMO);
        }
    }

    /** 耐久为 0 时的客户端开火拦截: disable 播放 dry_fire, remove 静默拦截(消失由服务端同步)。 */
    @Inject(method = "preCheck", at = @At("TAIL"), cancellable = true, remap = false, require = 0)
    private void taczfixes$blockNoDurability(IGun iGun, IGunOperator gunOperator, ClientGunIndex gunIndex,
                                             ItemStack mainHandItem, GunDisplayInstance display, GunData gunData,
                                             boolean playDrySound, CallbackInfoReturnable<ShootResult> cir) {
        if (cir.getReturnValue() != null) return;
        com.ssscript.taczfixes.common.data.GunTaczFixesData.DurabilityConfig cfg =
                com.ssscript.taczfixes.common.util.DurabilityStorage.config(mainHandItem);
        if (cfg == null || com.ssscript.taczfixes.common.util.DurabilityStorage.getMax(mainHandItem) <= 0) {
            return;
        }
        if (com.ssscript.taczfixes.common.util.DurabilityStorage.get(mainHandItem) > 0) {
            return;
        }
        String action = com.ssscript.taczfixes.common.util.DurabilityStorage.damageAction(cfg);
        if ("none".equals(action)) {
            return;
        }
        if (!"remove".equals(action) && playDrySound) {
            SoundPlayManager.playDryFireSound(player, display);
        }
        cir.setReturnValue(ShootResult.NO_AMMO);
    }
}
