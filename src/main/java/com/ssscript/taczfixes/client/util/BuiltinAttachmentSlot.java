package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.IStackTooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/** 改装界面中自定义槽位的原厂候选件(虚拟件, 不来自背包)。 */
public class BuiltinAttachmentSlot extends Button implements IStackTooltip {
    private final ItemStack stack;

    public BuiltinAttachmentSlot(int x, int y, ItemStack stack, Button.OnPress onPress) {
        super(x, y, 18, 18, Component.empty(), onPress, DEFAULT_NARRATION);
        this.stack = stack;
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    public void renderTooltip(Consumer<ItemStack> consumer) {
        if (this.isHoveredOrFocused()) {
            consumer.accept(this.stack);
        }
    }

    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int originalWidth = getWidth();
        int originalHeight = getHeight();
        float scale = originalWidth / (float) GunRefitScreen.SLOT_SIZE;
        boolean scaled = scale != 1f;
        if (scaled) {
            setWidth(GunRefitScreen.SLOT_SIZE);
            setHeight(GunRefitScreen.SLOT_SIZE);
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(getX(), getY(), 0);
            pose.scale(scale, scale, 1f);
            pose.translate(-getX(), -getY(), 0);
        }

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();

        int x = getX(), y = getY();
        if (isHoveredOrFocused()) {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x, y, 0, 0, width, height, 18, 18);
        } else {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x + 1, y + 1, 1, 1, width - 2, height - 2, 18, 18);
        }
        graphics.renderItem(this.stack, x + 1, y + 1);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        if (scaled) {
            graphics.pose().popPose();
            setWidth(originalWidth);
            setHeight(originalHeight);
        }
    }
}
