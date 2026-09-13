package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** 改装界面中的自定义槽位按钮。unavailable 时绘制 tacz 的不可用槽位图标。 */
public class CustomSlotButton extends Button implements com.tacz.guns.client.gui.components.refit.IStackTooltip {

    private final CustomSlotDefinition definition;
    private final String slotId;
    private final ItemStack gunStack;
    private final boolean unavailable;
    private final GunPackIconLoader.LoadedIcon iconTexture;
    private boolean selected;

    public CustomSlotButton(int x, int y, CustomSlotDefinition definition, String slotId, ItemStack gunStack,
                            boolean unavailable, OnPress onPress) {
        super(x, y, 18, 18, Component.literal(""), onPress, DEFAULT_NARRATION);
        this.definition = definition;
        this.slotId = slotId;
        this.gunStack = gunStack;
        this.unavailable = unavailable;
        GunPackIconLoader.LoadedIcon icon = null;
        if (definition.slot != null) {
            ResourceLocation tex = ResourceLocation.tryParse(definition.slot);
            if (tex != null) {
                icon = GunPackIconLoader.load(tex);
            }
        }
        this.iconTexture = icon;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.unavailable) {
            // 不可用槽位不响应点击, 同时清除可能残留的 focus(否则会保持选中样式的贴图)
            this.setFocused(false);
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderTooltip(java.util.function.Consumer<ItemStack> consumer) {
        if (!isHoveredOrFocused()) return;
        ItemStack item = CustomSlotStorage.get(gunStack, slotId);
        if (!item.isEmpty()) {
            consumer.accept(item);
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.selected = slotId.equals(CustomSlotGuiState.get());
        int x = getX();
        int y = getY();
        boolean hovered = isHoveredOrFocused();
        if (hovered) {
            Font font = Minecraft.getInstance().font;
            Component name = getSlotName();
            int nameX = x + (this.width - font.width(name)) / 2;
            int nameY = y + 20;
            if (this.selected && !CustomSlotStorage.get(gunStack, slotId).isEmpty()) {
                nameY = y + 30;
            }
            graphics.drawString(font, name, nameX, nameY, 0xFFFFFF);
        }
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        if (this.selected || hovered) {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x, y, 0, 0, this.width, this.height, 18, 18);
        } else {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x + 1, y + 1, 1, 1, this.width - 2, this.height - 2, 18, 18);
        }
        ItemStack item = CustomSlotStorage.get(gunStack, slotId);
        if (this.unavailable) {
            // tacz 不可用槽位图标(ICONS_TEXTURE offset 192)
            graphics.blit(GunRefitScreen.ICONS_TEXTURE, x + 2, y + 2,
                    this.width - 4, this.height - 4,
                    192f, 0f, 32, 32,
                    GunRefitScreen.getSlotsTextureWidth(), 32);
        } else if (definition.isCustom()) {
            if (item.isEmpty() && this.iconTexture != null) {
                graphics.blit(this.iconTexture.texture(),
                        x + 2, y + 2, 14, 14,
                        0f, 0f,
                        this.iconTexture.width(), this.iconTexture.height(),
                        this.iconTexture.width(), this.iconTexture.height());
            }
        } else {
            if (item.isEmpty() && this.iconTexture != null) {
                graphics.blit(this.iconTexture.texture(),
                        x + 2, y + 2, 14, 14,
                        0f, 0f,
                        this.iconTexture.width(), this.iconTexture.height(),
                        this.iconTexture.width(), this.iconTexture.height());
            } else if (item.isEmpty()) {
                try {
                    AttachmentType type = AttachmentType.valueOf(definition.type.toUpperCase());
                    int offset = GunRefitScreen.getSlotTextureXOffset(gunStack, type);
                    if (offset == 192) {
                        // 该类型未在枪械上启用: 仍显示此类型的槽位图标
                        offset = typeIconOffset(type);
                    }
                    if (offset >= 0) {
                        graphics.blit(GunRefitScreen.ICONS_TEXTURE, x + 2, y + 2,
                                this.width - 4, this.height - 4,
                                (float) offset, 0f, 32, 32,
                                GunRefitScreen.getSlotsTextureWidth(), 32);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (!this.unavailable && !item.isEmpty()) {
            graphics.renderItem(item, x + 1, y + 1);
        }
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private Component getSlotName() {
        if (definition.name != null && !definition.name.isEmpty()) {
            return Component.translatable(definition.name);
        }
        if (!definition.isCustom()) {
            return Component.translatable("tooltip.tacz.attachment." + definition.type.toLowerCase(Locale.US));
        }
        return Component.literal(slotId);
    }

    /** 与 GunRefitScreen.getSlotTextureXOffset 的类型图标偏移一致(枚举→switch 映射)。 */
    private static int typeIconOffset(AttachmentType type) {
        return switch (type) {
            case GRIP -> 0;
            case LASER -> 32;
            case MUZZLE -> 64;
            case SCOPE -> 96;
            case STOCK -> 128;
            case EXTENDED_MAG -> 160;
            default -> -1;
        };
    }
}
