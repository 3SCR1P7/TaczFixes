package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.client.util.AimingStaminaClientState;
import com.ssscript.taczfixes.common.network.ClientMessageHoldBreath;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/** 客户端: 屏息按键(默认左Ctrl)状态同步; 耐力耗尽时强制收镜。 */
public class HoldBreathKeyHandler {

    public static final KeyMapping HOLD_BREATH_KEY = new KeyMapping(
            "key.taczfixes.hold_breath",
            GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.categories.taczfixes");

    private static boolean lastSent = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            lastSent = false;
            AimingStaminaClientState.reset();
            return;
        }

        boolean holding = AimSwayHandler.canHoldBreath(player) && HOLD_BREATH_KEY.isDown();
        if (holding != lastSent && mc.getConnection() != null) {
            lastSent = holding;
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageHoldBreath(holding));
        }

        if (!Config.AIMING_STAMINA_ENABLED.get()) return;
        if (!AimingStaminaClientState.isKnown() || AimingStaminaClientState.getStamina() > 0f) return;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator != null && operator.isAim()) {
            operator.aim(false);
        }
    }
}
