package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.util.ItemNbt;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class GunLevelHandler {
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        DamageSource source = event.getSource();
        if (source == null) return;
        if (!(source.getDirectEntity() instanceof EntityKineticBullet bullet)) return;
        Entity owner = bullet.getOwner();
        if (!(owner instanceof Player player)) return;

        // 按子弹记录的枪械 id/射击手选择对应手枪(主手/副手), 避免经验给错枪
        // 优先使用子弹自身记录的射击手。双持同型号枪时仅凭 gunId 会永远选中副手。
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

        CompoundTag tag = ItemNbt.getOrCreate(gunStack);
        int currentExp = tag.getInt("GunLevelExp");
        int newExp = currentExp + 1;
        tag.putInt("GunLevelExp", newExp);

        int oldLevel = iGun.getLevel(currentExp);
        int newLevel = iGun.getLevel(newExp);
        tag.putInt("GunLevel", newLevel);
        if (newLevel > oldLevel && player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendToClientPlayer(new ServerMessageLevelUp(gunStack, newLevel), serverPlayer);
        }
    }
}
