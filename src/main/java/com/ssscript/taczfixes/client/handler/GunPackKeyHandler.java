package com.ssscript.taczfixes.client.handler;

import com.mojang.blaze3d.platform.InputConstants;
import com.ssscript.taczfixes.client.screen.GunPackScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/** Alt+G 打开枪包管理界面(修饰键由 KeyMapping 本身处理)。 */
public class GunPackKeyHandler {
    public static final KeyMapping GUN_PACK_KEY = new KeyMapping(
            "key.taczfixes.gun_packs",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.taczfixes");

    @SubscribeEvent
    public void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (false) return;
        com.ssscript.taczfixes.client.util.PendingResourceReload.tick();
        while (GUN_PACK_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.setScreen(GunPackScreen.create());
            }
        }
    }
}
