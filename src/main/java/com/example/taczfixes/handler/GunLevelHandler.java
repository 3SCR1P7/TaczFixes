package com.example.taczfixes.handler;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageLevelUp;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class GunLevelHandler {
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        var source = event.getSource();
        if (source == null) return;
        if (!(source.getDirectEntity() instanceof EntityKineticBullet bullet)) return;
        Entity owner = bullet.getOwner();
        if (!(owner instanceof Player player)) return;

        ItemStack gunStack = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) return;

        CompoundTag tag = gunStack.getOrCreateTag();
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
