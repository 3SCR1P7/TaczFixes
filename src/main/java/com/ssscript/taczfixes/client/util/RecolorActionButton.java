package com.ssscript.taczfixes.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 调色面板底部的小按钮(重置/完成)。 */
public class RecolorActionButton extends Button implements RecolorPanelWidget {

    public RecolorActionButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int background = isHovered ? 0xFF2A313D : 0xFF1E232C;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF2A303B);
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFE8ECF3);
    }
}
