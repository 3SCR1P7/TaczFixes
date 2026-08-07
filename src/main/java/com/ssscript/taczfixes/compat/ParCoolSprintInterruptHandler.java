package com.ssscript.taczfixes.compat;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.util.ParCoolHelper;
import com.tacz.guns.api.event.common.GunFireEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ParCoolSprintInterruptHandler {
    private static final Map<UUID, Long> LAST_FIRE_TIME = new ConcurrentHashMap<>();
    private static boolean listenersRegistered = false;
    private static Class<?> fastRunClass;
    private static Method eventGetAction;
    private static Method eventGetPlayer;

    @SubscribeEvent
    public void onGunFire(GunFireEvent event) {
        if (!Config.PARCOOL_INTERRUPT_SPRINT_ON_FIRE.get()) return;
        if (event.getLogicalSide().isServer()) return;
        if (!(event.getShooter() instanceof LocalPlayer player)) return;
        registerCancelListeners();
        LAST_FIRE_TIME.put(player.getUUID(), System.currentTimeMillis());
        player.setSprinting(false);
        ParCoolHelper.interruptSprint(player);
    }

    private static void registerCancelListeners() {
        if (listenersRegistered) return;
        listenersRegistered = true;
        if (!ParCoolHelper.isParCoolLoaded()) return;
        try {
            fastRunClass = Class.forName("com.alrex.parcool.common.action.impl.FastRun");
            Class<?> baseEvent = Class.forName("com.alrex.parcool.api.unstable.action.ParCoolActionEvent");
            eventGetAction = baseEvent.getMethod("getAction");
            eventGetPlayer = baseEvent.getMethod("getPlayer");
            Class<?> tryToStart = Class.forName("com.alrex.parcool.api.unstable.action.ParCoolActionEvent$TryToStartEvent");
            Class<?> tryToContinue = Class.forName("com.alrex.parcool.api.unstable.action.ParCoolActionEvent$TryToContinueEvent");
            Consumer<Event> cancel = ParCoolSprintInterruptHandler::cancelIfFastRun;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class) tryToStart, (Consumer) cancel);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class) tryToContinue, (Consumer) cancel);
        } catch (Exception e) {
            // ignore
        }
    }

    private static void cancelIfFastRun(Event event) {
        if (!Config.PARCOOL_INTERRUPT_SPRINT_ON_FIRE.get()) return;
        try {
            Object playerObj = eventGetPlayer.invoke(event);
            if (!(playerObj instanceof Player player)) return;
            Long last = LAST_FIRE_TIME.get(player.getUUID());
            if (last == null) return;
            long window = Config.PARCOOL_INTERRUPT_SPRINT_WINDOW_MS.get();
            if (System.currentTimeMillis() - last > window) {
                LAST_FIRE_TIME.remove(player.getUUID());
                return;
            }
            Object action = eventGetAction.invoke(event);
            if (action == null || !fastRunClass.isInstance(action)) return;
            event.setCanceled(true);
        } catch (Exception e) {
            // ignore
        }
    }
}
