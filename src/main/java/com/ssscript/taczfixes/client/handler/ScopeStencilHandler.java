package com.ssscript.taczfixes.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 在世界绘制前确保主渲染目标带模板缓冲: 否则第一次瞄具 renderScope 启用模板时会重建帧缓冲, 导致镜内(尤其是先渲染的副手)全黑。 */
public class ScopeStencilHandler {
    private static boolean taczfixes$ensured;

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || taczfixes$ensured) {
            return;
        }
        taczfixes$ensured = true;
        try {
            Minecraft.getInstance().getMainRenderTarget().enableStencil();
        } catch (Throwable ignored) {
            taczfixes$ensured = false;
        }
    }
}
