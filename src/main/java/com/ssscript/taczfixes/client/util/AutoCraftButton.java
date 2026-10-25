package com.ssscript.taczfixes.client.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class AutoCraftButton extends Button {

    private static final ResourceLocation GEAR =
            new ResourceLocation("taczfixes", "textures/gui/auto_craft_gear.png");

    private boolean active;
    private boolean enoughMaterials;
    private boolean coolingDown;
    private boolean powerPaused;

    public AutoCraftButton(int x, int y, OnPress onPress) {
        super(x, y, 20, 20, Component.empty(), onPress, Button.DEFAULT_NARRATION);
    }

    public void setStatus(boolean active, boolean enoughMaterials, boolean coolingDown, boolean powerPaused) {
        this.active = active;
        this.enoughMaterials = enoughMaterials;
        this.coolingDown = coolingDown;
        this.powerPaused = powerPaused;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int border;
        int background;
        if (this.active) {
            if (this.coolingDown || this.powerPaused) {
                border = 0xFFFFE000;
                background = 0xFFD4A800;
            } else if (this.enoughMaterials) {
                border = 0xFF00D000;
                background = 0xFF009000;
            } else {
                border = 0xFFFF3030;
                background = 0xFFC80000;
            }
        } else {
            border = 0xFF66FFFF;
            background = this.isHovered() ? 0xFF00CDCD : 0xFF3F3F3F;
        }

        graphics.fill(x, y, x + 20, y + 20, border);
        graphics.fill(x + 1, y + 1, x + 19, y + 19, 0xFFFFFFFF);
        graphics.fill(x + 2, y + 2, x + 18, y + 18, background);
        graphics.blit(GEAR, x + 4, y + 4, 0, 0, 12, 12, 12, 12);
    }
}
