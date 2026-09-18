package com.ssscript.taczfixes.client.handler;

import com.mojang.blaze3d.platform.InputConstants;
import com.ssscript.taczfixes.client.screen.GunDataEditScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class EditDataKeyHandler {
    public static final KeyMapping EDIT_DATA_KEY = new KeyMapping(
            "key.taczfixes.edit_data",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
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
        boolean down = EDIT_DATA_KEY.isDown();
        if (down && !wasDown) {
            net.minecraft.world.item.ItemStack mainHand = mc.player != null ? mc.player.getMainHandItem() : net.minecraft.world.item.ItemStack.EMPTY;
            if (mainHand.getItem() instanceof com.tacz.guns.api.item.IGun) {
                mc.setScreen(new GunDataEditScreen());
            }
        }
        wasDown = down;
    }
}
