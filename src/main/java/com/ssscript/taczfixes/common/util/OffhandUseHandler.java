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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OffhandUseHandler {
    private static final Map<UUID, ActiveUse> ACTIVE = new ConcurrentHashMap<>();

    private OffhandUseHandler() {
    }

    public static void setUsing(ServerPlayer player, boolean using) {
        UUID id = player.getUUID();
        if (!using) {
            ACTIVE.remove(id);
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        if (!canUse(player, offhand)) {
            ACTIVE.remove(id);
            return;
        }
        if (offhand.getUseDuration() == 0) {
            player.gameMode.useItem(player, player.serverLevel(), offhand, InteractionHand.OFF_HAND);
            return;
        }
        ACTIVE.put(id, new ActiveUse(offhand.copy(), Math.max(1, offhand.getUseDuration())));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ActiveUse active = ACTIVE.get(player.getUUID());
        if (active == null) {
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        if (!canUse(player, offhand) || !ItemStack.isSameItemSameTags(offhand, active.stack)) {
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
