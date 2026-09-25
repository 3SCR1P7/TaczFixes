package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.common.util.PausableClock;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 暂停(含失去焦点暂停)期间冻结 TACZ 的换弹/动画计时(渲染帧在暂停时仍在运行, 用它检测暂停状态)。 */
public class PauseClockHandler {

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            PausableClock.setPaused(Minecraft.getInstance().isPaused());
        }
    }
}
