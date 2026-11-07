package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.TaczFixesMod;
import com.tacz.guns.api.item.IGun;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = TaczFixesMod.MOD_ID)
public final class OffhandUseHandler {
    private static final Map<UUID, ActiveUse> ACTIVE = new ConcurrentHashMap<>();

    private OffhandUseHandler() {
    }

    /**
     * 副手物品使用完全交给 vanilla 的 ServerboundUseItemPacket / release 流程.
     * 这里只清理旧的 active 记录, 不能再次调用 gameMode.useItem / finishUsingItem:
     * 那会导致鸡蛋/雪球丢出两次, 或把喷溅/滞留药水当成饮用型药水结算.
     */
    public static void setUsing(ServerPlayer player, boolean using) {
        if (player == null) {
            return;
        }
        ACTIVE.remove(player.getUUID());
    }
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (false || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ActiveUse active = ACTIVE.get(player.getUUID());
        if (active == null) {
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        if (!canUse(player, offhand) || !ItemStack.isSameItemSameComponents(offhand, active.stack)) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        active.ticks++;
        if (active.ticks < active.duration) {
            return;
        }
        ACTIVE.remove(player.getUUID());
        offhand.finishUsingItem(player.level(), player);
    }

    private static boolean canUse(ServerPlayer player, ItemStack offhand) {
        return !offhand.isEmpty()
                && !(offhand.getItem() instanceof ShieldItem)
                && IGun.getIGunOrNull(player.getMainHandItem()) != null
                && IGun.getIGunOrNull(offhand) == null;
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    private static final class ActiveUse {
        final ItemStack stack;
        final int duration;
        int ticks;

        ActiveUse(ItemStack stack, int duration) {
            this.stack = stack;
            this.duration = duration;
        }
    }
}
