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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class GunLevelHandler {
    @SubscribeEvent
    public void onGunKill(EntityKillByGunEvent event) {
        if (event.getLogicalSide() != net.neoforged.fml.LogicalSide.SERVER) return;
        if (!(event.getAttacker() instanceof Player player)) return;
        if (event.getKilledEntity() == null) return;

        ItemStack gunStack;
        Entity bullet = event.getBullet();
        if (bullet instanceof OffhandBulletSource offhandSource) {
            gunStack = offhandSource.dualWield$isOffhandSource() ? player.getOffhandItem() : player.getMainHandItem();
            IGun held = IGun.getIGunOrNull(gunStack);
            if (held == null || !event.getGunId().equals(held.getGunId(gunStack))) {
                gunStack = GunEnchantmentHelper.getGunStack(event.getGunId(), player);
            }
        } else {
            gunStack = GunEnchantmentHelper.getGunStack(event.getGunId(), player);
        }
        if (gunStack.isEmpty()) return;
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) return;

        int currentExp = iGun.getExp(gunStack);
        int newExp = currentExp + 1;
        int oldLevel = iGun.getLevel(currentExp);
        int newLevel = iGun.getLevel(newExp);
        ItemNbt.mutate(gunStack, tag -> {
            tag.putInt("GunLevelExp", newExp);
            tag.putInt("GunLevel", newLevel);
        });
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getInventory().setChanged();
            serverPlayer.inventoryMenu.broadcastChanges();
            if (newLevel > oldLevel) {
                NetworkHandler.sendToClientPlayer(new ServerMessageLevelUp(gunStack, newLevel), serverPlayer);
            }
        }
    }
}
