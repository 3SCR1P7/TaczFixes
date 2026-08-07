package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import com.ssscript.taczfixes.util.MultishotHelper;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 对使用现代散射逻辑（ModernKineticGunItem.doBulletSpread，带 calcSpread 脚本）的枪械：
 * - 多重射击：补发额外弹丸（不消耗弹药）
 * - 激流：射手处于水中/雨中/气泡中时，提升子弹飞行速度
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunItem", remap = false)
public class MixinModernKineticGunDoBulletSpread {
    @Inject(method = "doBulletSpread", at = @At("HEAD"), remap = false)
    private void taczfixes$multishotAndRiptide(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter,
                                               Projectile projectile, int bulletCnt, float processedSpeed,
                                               float inaccuracy, float pitch, float yaw, CallbackInfo ci) {
        if (shooter == null || projectile == null) {
            return;
        }
        MultishotHelper.spawnExtraBullets(shooter, gunItem, projectile, bulletCnt, processedSpeed, inaccuracy, pitch, yaw);
    }

    @Inject(method = "doBulletSpread", at = @At("TAIL"), remap = false)
    private void taczfixes$riptideSpeed(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter,
                                        Projectile projectile, int bulletCnt, float processedSpeed,
                                        float inaccuracy, float pitch, float yaw, CallbackInfo ci) {
        if (shooter == null || projectile == null) {
            return;
        }
        float factor = GunEnchantmentHelper.getRiptideSpeedFactor(shooter);
        if (factor != 1.0f) {
            Vec3 motion = projectile.getDeltaMovement();
            projectile.setDeltaMovement(motion.scale(factor));
        }
    }
}
