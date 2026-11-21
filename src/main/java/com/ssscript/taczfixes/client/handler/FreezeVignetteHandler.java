package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 常凪冻结: 屏幕边缘叠加红色暗角(类似压制效果, 只是把变黑换成变红)。 */
public class FreezeVignetteHandler {
    private static final int RED = 0xE01810;
    private static final float STRENGTH = 0.62F;
    private static final int STRIPS = 16;

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        if (!FreezeClientState.isFrozen()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int band = Math.max(8, (int) (Math.min(width, height) * 0.30F));
        int alpha = Math.round(STRENGTH * 255.0F);
        // 上、下: 垂直渐变
        graphics.fillGradient(0, 0, width, band, withAlpha(alpha), 0);
        graphics.fillGradient(0, height - band, width, height, 0, withAlpha(alpha));
        // 左、右: 用竖条拼出水平渐变
        int strip = Math.max(1, band / STRIPS);
        for (int i = 0; i < STRIPS; i++) {
            int a = Math.round(alpha * (1.0F - (float) i / (float) STRIPS));
            int color = withAlpha(a);
            int left = i * strip;
            int right = width - (i + 1) * strip;
            graphics.fill(left, 0, left + strip, height, color);
            graphics.fill(right, 0, right + strip, height, color);
        }
    }

    private static int withAlpha(int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (RED & 0x00FFFFFF);
    }
}
