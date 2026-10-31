package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.util.ItemNbt;
import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class GunAnvilHandler {
    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!Config.GUN_BOTTLE_ENABLED.get()) {
            return;
        }
        ItemStack target = event.getLeft();
        ItemStack sacrifice = event.getRight();
        if (target.isEmpty() || sacrifice.isEmpty()) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(target);
        if (iGun == null) {
            return;
        }
        if (sacrifice.getItem() != Items.EXPERIENCE_BOTTLE) {
            return;
        }
        int count = sacrifice.getCount();
        int expPerBottle = Config.GUN_BOTTLE_EXP_PER_BOTTLE.get();
        if (count <= 0 || expPerBottle <= 0) {
            return;
        }

        ItemStack result = target.copy();
        CompoundTag tag = ItemNbt.getOrCreate(result);
        int newExp = tag.getInt("GunLevelExp") + count * expPerBottle;
        tag.putInt("GunLevelExp", newExp);
        tag.putInt("GunLevel", iGun.getLevel(newExp));

        int cost = Config.GUN_BOTTLE_COST.get();
        event.setOutput(result);
        event.setCost(cost);
        event.setMaterialCost(count);
    }
}