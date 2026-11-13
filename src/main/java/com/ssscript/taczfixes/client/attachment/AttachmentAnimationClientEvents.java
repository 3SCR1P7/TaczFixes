package com.ssscript.taczfixes.client.attachment;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 配件动画的客户端输入/生命周期事件。
 *
 * <p>TaCZ 自身的枪械状态机输入（shoot/reload/blot/...）由
 * {@code MixinAnimationStateMachineTrigger} 直接转发；开镜开始/结束没有对应的 TaCZ 输入，
 * 因此在这里按客户端 tick 检测并发送 {@code aim} / {@code aim_stop}。
 */
public final class AttachmentAnimationClientEvents {

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        AttachmentAnimationManager.clientTick();
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AttachmentAnimationManager.clearInstances();
    }
}
