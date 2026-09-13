package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.network.ClientMessageStaminaConsume;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.register.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** ParCool 耐力消耗拦截: 改为消耗本模组耐力。 */
public final class ParCoolStaminaConsumeHelper {

    private ParCoolStaminaConsumeHelper() {
    }

    /** 返回 true 表示取消 ParCool 自身消耗。 */
    public static boolean handle(Player player, int amount) {
        if (!Config.STAMINA_ENABLED.get()) return false;
        if (player == null || amount <= 0) return false;
        if (Minecraft.getInstance().player != player) return false;
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageStaminaConsume(amount));
        return true;
    }
}
