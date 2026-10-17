package com.ssscript.taczfixes.client.screen;

import com.ssscript.taczfixes.client.util.GunPackIconLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 主菜单/暂停界面上的枪包管理入口: 只有图标(TaCZ 默认枪包 logo), 无文字。 */
public final class GunPackMenuButton extends Button {
    private final GunPackIconLoader.LoadedIcon icon;

    public GunPackMenuButton(int x, int y, int size, GunPackIconLoader.LoadedIcon icon, Screen parent) {
        super(x, y, size, size, Component.empty(),
                button -> Minecraft.getInstance().setScreen(GunPackScreen.create(parent)),
                supplier -> supplier.get());
        this.icon = icon;
        setTooltip(Tooltip.create(Component.translatable("gui.taczfixes.gun_packs.title")));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (icon == null) {
            return;
        }
        int padding = Math.max(1, getWidth() / 10);
        int width = getWidth() - padding * 2;
        int height = getHeight() - padding * 2;
        graphics.blit(icon.texture(), getX() + padding, getY() + padding, width, height,
                0, 0, icon.width(), icon.height(), icon.width(), icon.height());
    }
}
