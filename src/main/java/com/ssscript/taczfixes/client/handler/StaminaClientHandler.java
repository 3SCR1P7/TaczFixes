package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.client.util.StaminaClientState;
import com.ssscript.taczfixes.common.network.ClientMessageStaminaConsumeRaw;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.util.ParCoolHelper;
import com.ssscript.taczfixes.common.util.ParCoolStaminaHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 客户端: 同步镜像到 ParCool(耐力为 0 时进入力竭); ParCool 滑铲消耗; 断线清理。 */
public class StaminaClientHandler {

    private static boolean wasSliding = false;
    private static boolean lastSprintingSent = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            StaminaClientState.reset();
            wasSliding = false;
            lastSprintingSent = false;
            return;
        }
        if (!Config.STAMINA_ENABLED.get()) return;

        boolean sliding = ParCoolHelper.isSliding(player);
        if (sliding && !wasSliding) {
            float cost = Config.STAMINA_SLIDE_COST.get().floatValue();
            if (cost > 0f) {
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageStaminaConsumeRaw(cost));
            }
        }
        wasSliding = sliding;

        if (!StaminaClientState.isKnown()) return;

        // 上报本地疾跑状态(每 tick 变化时发送), 服务端据此消耗
        boolean sprinting = player.isSprinting();
        if (sprinting != lastSprintingSent || player.tickCount % 20 == 0) {
            lastSprintingSent = sprinting;
            NetworkHandler.CHANNEL.sendToServer(new com.ssscript.taczfixes.common.network.ClientMessageSprintState(sprinting));
        }

        // 耐力耗尽时打断疾跑
        if (StaminaClientState.getStamina() <= 0f && player.isSprinting()) {
            player.setSprinting(false);
        }

        if (ParCoolStaminaHelper.isLoaded()) {
            ParCoolStaminaHelper.mirror(player, StaminaClientState.getStamina(), StaminaClientState.getMax(),
                    StaminaClientState.isExhausted());
        }
    }
}
