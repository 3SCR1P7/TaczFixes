package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.tacz.guns.api.item.IGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 兜底清理: 来源配件移除后遗留的孤儿自定义槽(每秒扫描一次玩家背包中的枪械)。 */
public class CustomSlotOrphanHandler {
    private static final int INTERVAL = 20;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % INTERVAL != 0) return;
        for (ItemStack stack : player.getInventory().items) {
            clean(player, stack);
        }
        clean(player, player.getMainHandItem());
        clean(player, player.getOffhandItem());
    }

    private static void clean(ServerPlayer player, ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof IGun) {
            CustomSlotManager.cascadeUnloadOrphans(player, stack);
        }
    }
}
