package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.register.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 黑色半透明扁平按钮：不使用原版 widgets 贴图，
 * 以纯色半透明矩形绘制背景，悬停时加亮。
 * 不透明度可在配置文件 misc.refit_button.opacity 中调整（0=全透明，1=不透明）。
 */
public class TransparentButton extends Button {

    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_HOVER = 0xFFFFFFFF;

    public TransparentButton(Component message, Button.OnPress onPress) {
        super(0, 0, 20, 20, message, onPress, DEFAULT_NARRATION);
    }

    public TransparentButton bounds(int x, int y, int w, int h) {
        setX(x);
        setY(y);
        setWidth(w);
        setHeight(h);
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hover = isHoveredOrFocused();
        double opacity = Math.min(1.0, Math.max(0.0, Config.REFIT_BUTTON_OPACITY.get()));
        int alpha = (int) (opacity * 255.0);
        int bg = (Math.min(255, Math.max(0, alpha)) << 24);
        int x = getX();
        int y = getY();
        graphics.fill(x, y, x + width, y + height, bg);
        if (hover) {
            // 悬停时绘制纯白色不透明描边(位于按钮外圈 1px)
            graphics.fill(x - 1, y - 1, x + width + 1, y, 0xFFFFFFFF);
            graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, 0xFFFFFFFF);
            graphics.fill(x - 1, y, x, y + height, 0xFFFFFFFF);
            graphics.fill(x + width, y, x + width + 1, y + height, 0xFFFFFFFF);
        }
        Component msg = getMessage();
        Minecraft mc = Minecraft.getInstance();
        graphics.drawCenteredString(mc.font, msg,
                x + width / 2, y + (height - 8) / 2, hover ? TEXT_HOVER : TEXT);
    }
}
