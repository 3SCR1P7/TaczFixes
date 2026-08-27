package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.Config;
import com.ssscript.taczfixes.common.TaczFixesMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PatienceHelper {
    private static final String TAG_MULT = "taczfixes_patience_mult";
    private static final Map<UUID, Long> LAST_FIRE_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Item> HELD_ITEM = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CLEAR_AT = new ConcurrentHashMap<>();
    private static final long CLEAR_DELAY_TICKS = 40;

    private PatienceHelper() {
    }

    public static void onShot(LivingEntity shooter, ItemStack gunItem, Projectile projectile) {
        if (shooter.level().isClientSide || gunItem == null || gunItem.isEmpty() || projectile == null) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(gunItem, TaczFixesMod.PATIENCE_ENCHANTMENT.get());
        float mult = computeMultiplier(shooter, level);
        if (mult != 1.0F) {
            projectile.getPersistentData().putFloat(TAG_MULT, mult);
        }
        LAST_FIRE_TICK.put(shooter.getUUID(), shooter.level().getGameTime());
    }

    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        UUID uuid = player.getUUID();
        long now = player.level().getGameTime();
        Item held = player.getMainHandItem().getItem();
        Item prev = HELD_ITEM.put(uuid, held);
        if (prev != null && prev != held) {
            CLEAR_AT.put(uuid, now + CLEAR_DELAY_TICKS);
        }
        Long clearAt = CLEAR_AT.get(uuid);
        if (clearAt != null && now >= clearAt) {
            LAST_FIRE_TICK.remove(uuid);
            CLEAR_AT.remove(uuid);
        }
    }

    public static float applyToBullet(Entity bullet, float amount) {
        if (bullet == null) {
            return amount;
        }
        CompoundTag tag = bullet.getPersistentData();
        if (tag != null && tag.contains(TAG_MULT)) {
            return amount * tag.getFloat(TAG_MULT);
        }
        return amount;
    }

    private static float computeMultiplier(LivingEntity shooter, int level) {
        if (level <= 0) {
            return 1.0F;
        }
        Long lastFire = LAST_FIRE_TICK.get(shooter.getUUID());
        if (lastFire == null) {
            return 1.0F;
        }
        long elapsed = shooter.level().getGameTime() - lastFire;
        int delayTicks = Math.max(Config.ENCH_PATIENCE_DELAY_MS.get() / 50, 1);
        long chargedTicks = Math.max(elapsed - delayTicks, 0);
        if (chargedTicks <= 0) {
            return 1.0F;
        }
        double percent = Config.ENCH_PATIENCE_DAMAGE_PERCENT_PER_TICK_PER_LEVEL.get() * level * chargedTicks;
        if (percent <= 0) {
            return 1.0F;
        }
        return 1.0F + (float) (percent / 100.0);
    }
}