package com.ssscript.taczfixes.handler;

import com.mojang.blaze3d.platform.InputConstants;
import com.ssscript.taczfixes.client.ConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

public class ConfigKeyHandler {
    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.taczfixes.open_config",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_S,
            "key.categories.taczfixes");

    private static boolean wasDown = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            wasDown = false;
            return;
        }
        boolean down = OPEN_CONFIG_KEY.isDown();
        if (down && !wasDown) {
            if (ModList.get().isLoaded("cloth_config")) {
                mc.setScreen(ConfigScreen.create(null));
            }
        }
        wasDown = down;
    }
}