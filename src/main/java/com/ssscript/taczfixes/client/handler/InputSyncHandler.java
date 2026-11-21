package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.common.network.ClientMessageInputSync;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.util.ShooterLuaHelper;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 本地按键状态变化时同步到服务端, 供服务端 Lua getInput() 读取。 */
public class InputSyncHandler {

    private static Set<String> lastSent = Collections.emptySet();
    private static boolean lastCharging = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            lastSent = Collections.emptySet();
            return;
        }
        if (mc.getConnection() == null) {
            return;
        }
        List<String> pressed = ShooterLuaHelper.pollPressedKeys();
        if (pressed == null) {
            return;
        }
        Set<String> current = new HashSet<>(pressed);
        boolean charging = taczfixes$isCharging(mc);
        if (current.equals(lastSent) && charging == lastCharging) {
            return;
        }
        lastSent = current;
        lastCharging = charging;
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageInputSync(new ArrayList<>(current), charging));
    }

    private static boolean taczfixes$isCharging(Minecraft mc) {
        try {
            var operator = com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);
            return operator != null && operator.getChargeProgress() > 0.001F;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
