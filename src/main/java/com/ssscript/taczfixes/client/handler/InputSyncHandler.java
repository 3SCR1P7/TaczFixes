package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.common.network.ClientMessageInputSync;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.util.ShooterLuaHelper;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 本地按键状态变化时同步到服务端, 供服务端 Lua getInput() 读取。 */
public class InputSyncHandler {

    private static Set<String> lastSent = Collections.emptySet();

    @SubscribeEvent
    public void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (false) return;
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
        if (current.equals(lastSent)) {
            return;
        }
        lastSent = current;
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageInputSync(new ArrayList<>(current)));
    }
}
