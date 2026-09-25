package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.ssscript.taczfixes.client.util.RefitViewMode;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 改装界面附加显示:
 * - 选中配件槽时隐藏 TaCZ Charms 的"绑定挂饰/卸下挂饰"按钮, 未选中时按原生显示;
 * - 维持改装点数(used/total)文字提示;
 * - 关闭界面时重置枪械模型拖动姿态。
 */
@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenViewMode extends Screen {

    private static final String CHARM_BUTTON_CLASS = "com.VvvV.taczcharms.client.gui.CharmPaletteButton";

    @Unique
    private int taczfixes$refitPointX = Integer.MIN_VALUE;

    protected MixinGunRefitScreenViewMode(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Inject(method = "addAttachmentTypeButtons", at = @At("TAIL"), remap = false)
    private void taczfixes$recordRefitPointAnchor(CallbackInfo ci) {
        int leftmost = Integer.MAX_VALUE;
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof AbstractWidget widget
                    && widget.getY() == 10
                    && (widget instanceof com.tacz.guns.client.gui.components.refit.GunAttachmentSlot
                    || widget instanceof com.ssscript.taczfixes.client.util.CustomSlotButton)) {
                leftmost = Math.min(leftmost, widget.getX());
            }
        }
        taczfixes$refitPointX = leftmost == Integer.MAX_VALUE ? Integer.MIN_VALUE : leftmost;
    }

    @Inject(method = "m_88315_", at = @At("HEAD"), remap = false)
    private void taczfixes$onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                    CallbackInfo ci) {
        boolean slotSelected = RefitTransform.getCurrentTransformType() != AttachmentType.NONE
                || CustomSlotGuiState.get() != null;
        for (GuiEventListener listener : this.children()) {
            if (listener instanceof AbstractWidget widget
                    && widget.getClass().getName().equals(CHARM_BUTTON_CLASS)) {
                widget.visible = !slotSelected;
                widget.active = !slotSelected;
            }
        }
        taczfixes$renderRefitPoint(graphics);
    }

    @Unique
    private void taczfixes$renderRefitPoint(GuiGraphics graphics) {
        if (taczfixes$refitPointX == Integer.MIN_VALUE) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        Integer total = TaczFixesDataManager.getGunRefitPoint(gunStack);
        if (total == null) return;
        int used = AttachmentTaczFixesManager.getRefitPointUsed(gunStack);
        Component text = Component.literal(used + "/" + total);
        int x = taczfixes$refitPointX - this.font.width(text)
                - com.ssscript.taczfixes.client.util.RefitSlotLayout.scaled(6);
        int y = 10 + (com.ssscript.taczfixes.client.util.RefitSlotLayout.size() - this.font.lineHeight) / 2;
        graphics.drawString(this.font, text, x, y, 0xFFFFFFFF, true);
    }

    @Inject(method = "m_7379_", at = @At("TAIL"), remap = false)
    private void taczfixes$onClose(CallbackInfo ci) {
        // 与收枪动画同步, 缓动重置模型旋转/平移/缩放
        RefitViewMode.beginResetQuick();
    }
}
