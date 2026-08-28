package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.accessor.EntityKineticBulletAccessor;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.ssscript.taczfixes.common.util.MultishotHelper;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.api.item.gun.AbstractGunItem", remap = false)
public class MixinAbstractGunDoBulletSpread {
    @Redirect(method = "m_142422_", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/resource/pojo/data/gun/GunData;getAmmoId()Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation taczfixes$ammoReplaceTooltip(GunData gunData, ItemStack stack) {
        ResourceLocation id = gunData.getAmmoId();
        ResourceLocation replaced = com.ssscript.taczfixes.common.util.AmmoReplaceHelper.resolveAmmoId(stack, id);
        return replaced != null ? replaced : id;
    }
    @ModifyVariable(method = "doBulletSpread", at = @At("HEAD"), argsOnly = true, index = 7)
    private float taczfixes$coilInaccuracy(float inaccuracy, ShooterDataHolder dataHolder, ItemStack gunItem,
                                           LivingEntity shooter, Projectile projectile, int bulletCnt,
                                           float processedSpeed, float originalInaccuracy, float pitch, float yaw) {
        if (gunItem == null) {
            return inaccuracy;
        }
        float coilFactor = GunEnchantmentHelper.getCoilInaccuracyFactor(gunItem);
        return coilFactor != 1.0f ? inaccuracy * coilFactor : inaccuracy;
    }

    @Inject(method = "doBulletSpread", at = @At("HEAD"), remap = false)
    private void taczfixes$multishotAndRiptide(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter,
                                               Projectile projectile, int bulletCnt, float processedSpeed,
                                               float inaccuracy, float pitch, float yaw, CallbackInfo ci) {
        if (shooter == null || projectile == null) {
            return;
        }
        MultishotHelper.spawnExtraBullets(shooter, gunItem, projectile, bulletCnt, processedSpeed, inaccuracy, pitch, yaw);
        com.ssscript.taczfixes.common.util.DoubleShotHelper.spawnExtraBullet(shooter, gunItem, projectile,
                processedSpeed, inaccuracy, pitch, yaw);
        com.ssscript.taczfixes.common.util.PatienceHelper.onShot(shooter, gunItem, projectile);
        if (projectile instanceof com.tacz.guns.entity.EntityKineticBullet kineticBullet && gunItem != null) {
            net.minecraft.resources.ResourceLocation replaced = com.ssscript.taczfixes.common.util.AmmoReplaceHelper
                    .resolveAmmoId(gunItem, kineticBullet.getAmmoId());
            if (replaced != null) {
                ((EntityKineticBulletAccessor) kineticBullet).taczfixes$setAmmoId(replaced);
            }
        }
    }

    @Inject(method = "doBulletSpread", at = @At("TAIL"), remap = false)
    private void taczfixes$riptideSpeed(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter,
                                        Projectile projectile, int bulletCnt, float processedSpeed,
                                        float inaccuracy, float pitch, float yaw, CallbackInfo ci) {
        if (shooter == null || projectile == null) {
            return;
        }
        float factor = GunEnchantmentHelper.getRiptideSpeedFactor(shooter);
        factor *= GunEnchantmentHelper.getCoilSpeedFactor(gunItem);
        if (factor != 1.0f) {
            Vec3 motion = projectile.getDeltaMovement();
            projectile.setDeltaMovement(motion.scale(factor));
        }
    }
}
