package com.ssscript.taczfixes.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 调色面板里的动态迷彩名称按钮: 居中显示、超宽省略, 点击切换到下一种。 */
public class RecolorCamoButton extends Button implements RecolorPanelWidget {

    public RecolorCamoButton(int x, int y, int width, int height, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int background = isHovered ? 0xFF2A313D : 0xFF1E232C;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF2A303B);
        Minecraft minecraft = Minecraft.getInstance();
        String text = getMessage().getString();
        int maxWidth = getWidth() - 6;
        if (minecraft.font.width(text) > maxWidth) {
            text = minecraft.font.plainSubstrByWidth(text, Math.max(0, maxWidth - minecraft.font.width("…"))) + "…";
        }
        graphics.drawCenteredString(minecraft.font, text, getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2, 0xFFE8ECF3);
    }
}
