package com.example.taczfixes.handler;

import com.example.taczfixes.Config;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * 铁砧合成：枪械 + 附魔之瓶。
 * 每瓶附魔之瓶为枪械增加固定经验值（GunLevelExp），每瓶固定消耗玩家 1 经验等级；
 * 格内多瓶可一次同时敲上（全部消耗并累计经验）。
 */
public class GunAnvilHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

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
        CompoundTag tag = result.getOrCreateTag();
        int newExp = tag.getInt("GunLevelExp") + count * expPerBottle;
        tag.putInt("GunLevelExp", newExp);
        tag.putInt("GunLevel", iGun.getLevel(newExp));

        int cost = Config.GUN_BOTTLE_COST.get();
        event.setOutput(result);
        event.setCost(cost);
        event.setMaterialCost(count);
        LOGGER.info("taczfixes: anvil bottle applied. count={}, newExp={}, level={}, cost={}",
                count, newExp, tag.getInt("GunLevel"), cost);
    }
}