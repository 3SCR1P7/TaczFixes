package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.accessor.EntityKineticBulletAccessor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MultishotHelper {
    private static final ThreadLocal<Boolean> FIRED_THIS_SHOT = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Map<UUID, Long> LAST_TRIGGER = new ConcurrentHashMap<>();

    private MultishotHelper() {
    }

    public static void onShotStart() {
        FIRED_THIS_SHOT.set(Boolean.FALSE);
    }

    public static void spawnExtraBullets(LivingEntity shooter, ItemStack gunItem, Projectile projectile,
                                         int bulletCnt, float processedSpeed, float inaccuracy,
                                         float pitch, float yaw) {
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        if (bulletCnt != 0 || FIRED_THIS_SHOT.get()) {
            return;
        }
        FIRED_THIS_SHOT.set(Boolean.TRUE);
        if (!(projectile instanceof EntityKineticBullet bullet)) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(gunItem, Enchantments.MULTISHOT);
        if (level <= 0) {
            return;
        }
        int extraCount = Config.ENCH_MULTISHOT_EXTRA_COUNT.get();
        if (extraCount <= 0) {
            return;
        }
        double chance = Config.ENCH_MULTISHOT_TRIGGER_CHANCE.get() * level;
        if (chance < 1.0 && shooter.getRandom().nextDouble() >= chance) {
            return;
        }
        long now = System.currentTimeMillis();
        int cooldown = Config.ENCH_MULTISHOT_COOLDOWN_MS.get();
        UUID key = shooter.getUUID();
        Long last = LAST_TRIGGER.get(key);
        if (last != null && now - last < cooldown) {
            return;
        }
        LAST_TRIGGER.put(key, now);

        ResourceLocation ammoId = bullet.getAmmoId();
        ResourceLocation gunId = bullet.getGunId();
        ResourceLocation gunDisplayId = bullet.getGunDisplayId();
        boolean isTracer = bullet.isTracerAmmo();
        float damageMultiplier = ((EntityKineticBulletAccessor) bullet).taczfixes$getShotDamageMultiplier();
        float speedFactor = GunEnchantmentHelper.getRiptideSpeedFactor(shooter);
        Level world = shooter.level();

        float spreadDegrees = Config.ENCH_MULTISHOT_SPREAD_ANGLE.get().floatValue();
        for (int i = 0; i < extraCount; i++) {
            final int side = i % 2 == 0 ? -1 : 1;
            final int tier = (i / 2) + 1;
            TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
                EntityKineticBullet extra = new EntityKineticBullet(world, shooter, gunItem, ammoId, gunId,
                        gunDisplayId, isTracer, index.getGunData(), index.getBulletData());
                extra.setShotDamageMultiplier(damageMultiplier);
                float yawOffset = side * spreadDegrees * tier;
                extra.shootFromRotation(shooter, pitch, yaw + yawOffset, 0.0F, processedSpeed, inaccuracy);
                if (speedFactor != 1.0f) {
                    extra.setDeltaMovement(extra.getDeltaMovement().scale(speedFactor));
                }
                world.addFreshEntity(extra);
            });
        }
    }
}
