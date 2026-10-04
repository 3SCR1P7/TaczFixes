package com.ssscript.taczfixes.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 调色面板中的单个颜色分组行: 色块 + 名称/占比, 点击选中。 */
public class RecolorClusterButton extends Button implements RecolorPanelWidget {

    private final int index;

    public RecolorClusterButton(int x, int y, int width, int height, int index) {
        super(x, y, width, height, Component.empty(), button -> GunRecolorGuiState.select(index), DEFAULT_NARRATION);
        this.index = index;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean selected = GunRecolorGuiState.selected() == index;
        int background = selected ? 0xFF24344C : (isHovered ? 0xFF20262F : 0xFF181D24);
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        if (selected) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, 0xFF4F8CFF);
        }
        int rgb = GunRecolorGuiState.colorOf(index);
        graphics.fill(getX() + 3, getY() + 3, getX() + 17, getY() + getHeight() - 3, 0xFF000000 | rgb);
        graphics.drawString(Minecraft.getInstance().font, GunRecolorGuiState.labelOf(index),
                getX() + 21, getY() + (getHeight() - 8) / 2, 0xFFE8ECF3, false);
    }
}
