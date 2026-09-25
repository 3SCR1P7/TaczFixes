package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class ScopeSwitchHandler {
    public static final KeyMapping SWITCH_SCOPE_KEY = new KeyMapping(
            "key.taczfixes.switch_scope",
            GLFW.GLFW_KEY_B,
            "key.categories.taczfixes");

    private static boolean wasDown = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean down = SWITCH_SCOPE_KEY.isDown();
        if (down && !wasDown) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || !player.isAlive()) return;
            ItemStack gun = player.getMainHandItem();
            if (IGun.getIGunOrNull(gun) == null) return;
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
            if (operator == null) return;
            if (operator.getClientAimingProgress(Minecraft.getInstance().getFrameTime()) < 0.01f) return;
            ScopeSwitchState.cycle(gun);
        }
        wasDown = down;
    }
}
