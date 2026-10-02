package com.ssscript.taczfixes.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 在世界绘制前确保主渲染目标带模板缓冲: 否则第一次瞄具 renderScope 启用模板时会重建帧缓冲, 导致镜内(尤其是先渲染的副手)全黑。 */
public class ScopeStencilHandler {
    private static boolean taczfixes$ensured;
    private static int taczfixes$ensuredWidth = -1;
    private static int taczfixes$ensuredHeight = -1;

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        var target = Minecraft.getInstance().getMainRenderTarget();
        // 帧缓冲在窗口尺寸变化/资源重载后会被重建, 需要重新启用模板
        if (taczfixes$ensured && target.width == taczfixes$ensuredWidth && target.height == taczfixes$ensuredHeight) {
            return;
        }
        taczfixes$ensured = false;
        try {
            target.enableStencil();
            taczfixes$ensuredWidth = target.width;
            taczfixes$ensuredHeight = target.height;
            taczfixes$ensured = true;
        } catch (Throwable ignored) {
        }
    }
}
