package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 世界画面中心短暂显示一行白色小字: 0.5s 全显, 0.5s 线性淡出。 */
public class CenterMessage {
    private static Component message = null;
    private static long startMs = 0L;

    private CenterMessage() {
    }

    public static void show(Component text) {
        message = text;
        startMs = System.currentTimeMillis();
    }

    public static void tick() {
        if (message != null && System.currentTimeMillis() - startMs >= 1000L) {
            message = null;
        }
    }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null || message == null) return;
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed >= 1000L) {
            message = null;
            return;
        }
        float alpha = elapsed <= 500L ? 1.0f : (float) Math.max(0.0, 1.0 - (elapsed - 500L) / 500.0);
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = (int) (mc.getWindow().getGuiScaledHeight() * 0.4);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        graphics.drawCenteredString(mc.font, message, cx, cy, 0xFFFFFFFF);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
