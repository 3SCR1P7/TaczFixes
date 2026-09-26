package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.item.ModernKineticGunItem;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector2d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import com.ssscript.taczfixes.common.util.OffhandBulletSource;
import net.minecraft.world.item.ItemStack;

@Mixin(value = {ModernKineticGunItem.class}, remap = false)
public abstract class MixinModernKineticGunItemSpread {
    @Inject(method = {"lambda$doBulletSpread$29(Lcom/tacz/guns/entity/EntityKineticBullet;Lnet/minecraft/world/entity/LivingEntity;FFFF)V"}, at = {@At("HEAD")}, cancellable = true, require = ServerMessageOffhandActionResult.ACTION_RELOAD)
    private static void dualWield$applyDefaultSpread(EntityKineticBullet bullet, LivingEntity shooter, float pitch, float yaw, float processedSpeed, float inaccuracy, CallbackInfo callback) {
        float multiplier = dualWield$getHipFireMultiplier(bullet, shooter);
        if (multiplier == 1.0f) {
            return;
        }
        bullet.shootFromRotation(shooter, pitch, yaw, 0.0f, processedSpeed, inaccuracy * multiplier);
        callback.cancel();
    }

    @Inject(method = {"lambda$doBulletSpread$28(Lcom/tacz/guns/entity/EntityKineticBullet;Lnet/minecraft/world/entity/LivingEntity;FFFLorg/joml/Vector2d;)V"}, at = {@At("HEAD")}, cancellable = true, require = ServerMessageOffhandActionResult.ACTION_RELOAD)
    private static void dualWield$applyScriptSpread(EntityKineticBullet bullet, LivingEntity shooter, float pitch, float yaw, float processedSpeed, Vector2d spread, CallbackInfo callback) {
        float multiplier = dualWield$getHipFireMultiplier(bullet, shooter);
        if (multiplier == 1.0f) {
            return;
        }
        Vector2d adjustedSpread = new Vector2d(spread).mul(multiplier);
        bullet.shootFromRotation(shooter, pitch, yaw, 0.0f, processedSpeed, adjustedSpread);
        callback.cancel();
    }

    @org.spongepowered.asm.mixin.Unique
    private static float dualWield$getHipFireMultiplier(EntityKineticBullet bullet, LivingEntity shooter) {
        if (shooter == null || !DualWieldEligibility.isDualWielding(shooter)) {
            return 1.0f;
        }
        boolean offhand = bullet instanceof OffhandBulletSource source && source.dualWield$isOffhandSource();
        ItemStack firedStack = offhand ? shooter.getOffhandItem() : shooter.getMainHandItem();
        if (!offhand && InaccuracyType.getInaccuracyType(shooter) == InaccuracyType.AIM) {
            return (float) DualWieldOverrides.withFallback(DualWieldOverrides.focusAimInaccuracyMultiplier(firedStack), DualWieldEligibility.getServerFocusAimInaccuracyMultiplier());
        }
        return (float) DualWieldOverrides.withFallback(DualWieldOverrides.inaccuracyMultiplier(firedStack), DualWieldEligibility.getServerInaccuracyMultiplier());
    }
}
