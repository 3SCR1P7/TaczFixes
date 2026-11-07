package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.ssscript.taczfixes.common.util.OffhandBulletSource;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageLevelUp;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class GunLevelHandler {
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        DamageSource source = event.getSource();
        if (source == null) return;
        if (!(source.getDirectEntity() instanceof EntityKineticBullet bullet)) return;
        Entity owner = bullet.getOwner();
        if (!(owner instanceof Player player)) return;
        LivingEntity killed = event.getEntity();

        // 按子弹记录的枪械 id/射击手选择对应枪械(主手/副手), 避免经验给错枪。
        ItemStack gunStack;
        if (bullet instanceof OffhandBulletSource offhandSource) {
            gunStack = offhandSource.dualWield$isOffhandSource() ? player.getOffhandItem() : player.getMainHandItem();
            IGun held = IGun.getIGunOrNull(gunStack);
            if (held == null || !bullet.getGunId().equals(held.getGunId(gunStack))) {
                gunStack = GunEnchantmentHelper.getGunStack(bullet.getGunId(), player);
            }
        } else {
            gunStack = GunEnchantmentHelper.getGunStack(bullet.getGunId(), player);
        }
        if (gunStack.isEmpty()) return;
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) return;

        int gained = randomInclusive(player, Config.GUN_KILL_XP_MIN.get(), Config.GUN_KILL_XP_MAX.get());
        // 目标实体死亡时原版会掉落的玩家经验: 每 1 点额外给配置范围内的随机枪械经验。
        int playerXpDrop = killed.getExperienceReward();
        if (playerXpDrop > 0) {
            int dropBonus = 0;
            int bonusMin = Config.GUN_PLAYER_XP_BONUS_MIN.get();
            int bonusMax = Config.GUN_PLAYER_XP_BONUS_MAX.get();
            for (int i = 0; i < playerXpDrop; i++) {
                dropBonus += randomInclusive(player, bonusMin, bonusMax);
            }
            gained += dropBonus;
        }

        CompoundTag tag = gunStack.getOrCreateTag();
        int currentExp = tag.getInt("GunLevelExp");
        long combined = (long) currentExp + Math.max(0, gained);
        int newExp = combined >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) combined;
        tag.putInt("GunLevelExp", newExp);

        int oldLevel = iGun.getLevel(currentExp);
        int newLevel = iGun.getLevel(newExp);
        tag.putInt("GunLevel", newLevel);
        if (newLevel > oldLevel && player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendToClientPlayer(new ServerMessageLevelUp(gunStack, newLevel), serverPlayer);
        }
    }

    /** 闭区间随机; 参数会先按非负、大小顺序修正。 */
    private static int randomInclusive(Player player, int first, int second) {
        int min = Math.max(0, Math.min(first, second));
        int max = Math.max(0, Math.max(first, second));
        long range = (long) max - min + 1L;
        if (range <= 1L) {
            return min;
        }
        if (range > Integer.MAX_VALUE) {
            range = Integer.MAX_VALUE;
        }
        return min + player.getRandom().nextInt((int) range);
    }
}
