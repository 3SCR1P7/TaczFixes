package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.CustomFireModeManager;
import com.ssscript.taczfixes.common.util.BurstBlockHelper;
import com.ssscript.taczfixes.common.util.OffhandGunPropertyResolver;
import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(LivingEntityShoot.class)
public class MixinLivingEntityShoot {
    @Shadow(remap = false)
    private ShooterDataHolder data;

    @Shadow(remap = false)
    private net.minecraft.world.entity.LivingEntity shooter;

    /** 上肢耐力不足时无法开火。 */
    @Inject(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$blockShootLowAimingStamina(CallbackInfoReturnable<ShootResult> cir) {
        if (!(shooter instanceof net.minecraft.server.level.ServerPlayer player)) return;
        com.ssscript.taczfixes.common.data.GunTaczFixesData.AimingStaminaConfig cfg =
                com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager.resolveAimingStamina(player.getMainHandItem());
        if (com.ssscript.taczfixes.common.handler.AimingStaminaHandler.isInsufficient(player, cfg.shoot_cost.floatValue())) {
            cir.setReturnValue(ShootResult.COOL_DOWN);
        }
    }

    /** 开火消耗上肢耐力(仅成功射击)。 */
    @Inject(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At("RETURN"), remap = false)
    private void taczfixes$consumeShootAimingStamina(CallbackInfoReturnable<ShootResult> cir) {
        if (cir.getReturnValue() != ShootResult.SUCCESS) return;
        if (!(shooter instanceof net.minecraft.server.level.ServerPlayer player)) return;
        com.ssscript.taczfixes.common.data.GunTaczFixesData.AimingStaminaConfig cfg =
                com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager.resolveAimingStamina(player.getMainHandItem());
        com.ssscript.taczfixes.common.handler.AimingStaminaHandler.consume(player, cfg.shoot_cost.floatValue());
    }

    @Inject(method = "shoot", at = @At("HEAD"), cancellable = true, remap = false)
    private void onBurstRestricted(CallbackInfoReturnable<ShootResult> cir) {
        if (data.currentGunItem == null) return;
        ItemStack gunItem = data.currentGunItem.get();
        if (!(gunItem.getItem() instanceof IGun iGun)) return;
        if (iGun.getFireMode(gunItem) != FireMode.BURST) return;
        if (!BurstBlockHelper.hasRestrictedAttachment(iGun, gunItem)) return;
        cir.setReturnValue(ShootResult.NO_AMMO);
    }

    /** 服务端射击全程激活自定义模式: 冷却/连发数据查询(在 shootOnce 之前读 min_interval)均按 id 生效。 */
    @Inject(method = "shoot", at = @At("HEAD"), remap = false)
    private void taczfixes$activateServer(CallbackInfoReturnable<ShootResult> cir) {
        ItemStack gunItem = data == null || data.currentGunItem == null ? null : data.currentGunItem.get();
        CustomFireModeManager.activateFor(gunItem);
    }

    @Inject(method = "shoot", at = @At("RETURN"), remap = false)
    private void taczfixes$deactivateServer(CallbackInfoReturnable<ShootResult> cir) {
        CustomFireModeManager.resetActive();
    }

    @Inject(method = {"getShootCoolDown(J)J"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private void dualWield$getOffhandShootCoolDown(long timestamp, CallbackInfoReturnable<Long> callback) {
        if (!OffhandShooterManager.isOffhandData(this.data) || !OffhandShooterManager.isCurrentOffhandContext(this.shooter, this.data)) {
            return;
        }
        if (this.data.currentGunItem == null) {
            callback.setReturnValue(0L);
            return;
        }
        ItemStack stack = this.data.currentGunItem.get();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            callback.setReturnValue(0L);
            return;
        }
        GunData gunData = TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> {
            return index.getGunData();
        }).orElse(null);
        if (gunData == null) {
            callback.setReturnValue(-1L);
            return;
        }
        FireMode fireMode = gun.getFireMode(stack);
        long interval = OffhandGunPropertyResolver.getShootInterval(stack, gun, gunData, fireMode, this.data.cacheProperty);
        if (interval < 0) {
            callback.setReturnValue(-1L);
        } else {
            long elapsed = timestamp - this.data.shootTimestamp;
            callback.setReturnValue(Long.valueOf(Math.max((interval - elapsed) - 5, 0L)));
        }
    }
}
