package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.Config;
import com.ssscript.taczfixes.common.TaczFixesMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DoubleShotHelper {
    private static final ThreadLocal<Boolean> ROLLED_THIS_SHOT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DoubleShotHelper() {
    }

    public static void onShotStart() {
        ROLLED_THIS_SHOT.set(Boolean.FALSE);
    }

    public static void spawnExtraBullet(LivingEntity shooter, ItemStack gunItem, Projectile projectile,
                                        float processedSpeed, float inaccuracy, float pitch, float yaw) {
        if (!GunEnchantmentHelper.isEnabled() || ROLLED_THIS_SHOT.get()) {
            return;
        }
        ROLLED_THIS_SHOT.set(Boolean.TRUE);
        if (!(projectile instanceof EntityKineticBullet bullet)) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(gunItem, TaczFixesMod.DOUBLE_SHOT_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        double chance = Config.ENCH_DOUBLE_SHOT_CHANCE_PER_LEVEL.get() * level;
        if (chance >= 1.0 || shooter.getRandom().nextDouble() < chance) {
            spawnExtra(shooter, gunItem, bullet, processedSpeed, inaccuracy, pitch, yaw);
        }
    }

    private static void spawnExtra(LivingEntity shooter, ItemStack gunItem, EntityKineticBullet bullet,
                                   float processedSpeed, float inaccuracy, float pitch, float yaw) {
        net.minecraft.resources.ResourceLocation ammoId = bullet.getAmmoId();
        net.minecraft.resources.ResourceLocation gunId = bullet.getGunId();
        net.minecraft.resources.ResourceLocation gunDisplayId = bullet.getGunDisplayId();
        boolean isTracer = bullet.isTracerAmmo();
        float damageMultiplier = ((com.ssscript.taczfixes.common.mixin.EntityKineticBulletAccessor) bullet)
                .taczfixes$getShotDamageMultiplier();
        Level world = shooter.level();
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
            EntityKineticBullet extra = new EntityKineticBullet(world, shooter, gunItem, ammoId, gunId,
                    gunDisplayId, isTracer, index.getGunData(), index.getBulletData());
            extra.setShotDamageMultiplier(damageMultiplier);
            extra.shootFromRotation(shooter, pitch, yaw, 0.0F, processedSpeed, inaccuracy);
            world.addFreshEntity(extra);
        });
    }
}