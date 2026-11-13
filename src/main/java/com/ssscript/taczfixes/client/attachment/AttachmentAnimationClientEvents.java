package com.ssscript.taczfixes.client.attachment;

import com.ssscript.taczfixes.TaczFixesMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 配件动画的客户端输入/生命周期事件。
 *
 * <p>TaCZ 自身的枪械状态机输入（shoot/reload/blot/...）由
 * {@code MixinAnimationStateMachineTrigger} 直接转发；开镜开始/结束没有对应的 TaCZ 输入，
 * 因此在这里按客户端 tick 检测并发送 {@code aim} / {@code aim_stop}。
 */
@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT)
public final class AttachmentAnimationClientEvents {
    private AttachmentAnimationClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        AttachmentAnimationManager.clientTick();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AttachmentAnimationManager.clearInstances();
    }
}
