package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.common.network.ClientMessageOffhandAction;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 双持时把 Arcana 的 ACTION_1..4 按键额外转发一次为副手动作 (ON_ACTION_1..4 桥接)。
 * 按翻译键匹配 Arcana 注册的按键, 不需要依赖其混淆类。
 */
public class ArcanaOffhandKeyHandler {

    private static final String[] ACTION_KEYS = {
            "key.taczexpands.action_1.desc",
            "key.taczexpands.action_2.desc",
            "key.taczexpands.action_3.desc",
            "key.taczexpands.action_4.desc"
    };

    @SubscribeEvent
    public void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || !minecraft.mouseHandler.isMouseGrabbed() || !minecraft.isWindowActive()) {
            return;
        }
        if (!DualWieldEligibility.isDualWielding(player)) {
            return;
        }
        int action = matchActionKey(minecraft, event);
        if (action <= 0) {
            return;
        }
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageOffhandAction(action));
    }

    private static int matchActionKey(Minecraft minecraft, InputEvent.Key event) {
        for (int i = 0; i < ACTION_KEYS.length; i++) {
            for (KeyMapping mapping : minecraft.options.keyMappings) {
                if (ACTION_KEYS[i].equals(mapping.getName()) && mapping.matches(event.getKey(), event.getScanCode())) {
                    return i + 1;
                }
            }
        }
        return 0;
    }
}
