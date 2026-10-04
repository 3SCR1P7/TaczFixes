package com.ssscript.taczfixes.client.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** 左侧调色面板背景(同时拦截模型拖动点击)。 */
public class RecolorPanelBackground extends AbstractWidget implements RecolorPanelWidget {

    public RecolorPanelBackground(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    /** 仅作背景与拖动拦截判定, 不消费点击, 否则会挡住面板内的按钮/滑条。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xE0101418);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
