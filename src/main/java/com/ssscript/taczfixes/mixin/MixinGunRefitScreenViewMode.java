package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.RefitViewMode;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenViewMode extends Screen {

    protected MixinGunRefitScreenViewMode(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Unique
    private Button taczfixes$viewModeButton;

    @Inject(method = "addAttachmentTypeButtons", at = @At("TAIL"), remap = false)
    private void taczfixes$ensureViewModeButton(CallbackInfo ci) {
        int leftmost = Integer.MAX_VALUE;
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof AbstractWidget widget
                    && widget.getY() == 10 && widget.getHeight() == 18) {
                leftmost = Math.min(leftmost, widget.getX());
            }
        }
        if (leftmost == Integer.MAX_VALUE) {
            leftmost = this.width - 30 - 18 * 6;
        }
        Component label = Component.translatable(RefitViewMode.isActive()
                ? "gui.taczfixes.refit_view.exit" : "gui.taczfixes.refit_view.enter");
        int width = this.font.width(label) + 10;
        int x = leftmost - width - 4;
        Button button = Button.builder(label, b -> taczfixes$toggleViewMode())
                .bounds(x, 10, width, 18).build();
        this.addRenderableWidget(button);
        taczfixes$viewModeButton = button;
        RefitViewMode.setButtonBounds(x, 10, width, 18);
    }

    @Unique
    private void taczfixes$toggleViewMode() {
        boolean active = !RefitViewMode.isActive();
        RefitViewMode.setActive(active);
        if (taczfixes$viewModeButton != null) {
            taczfixes$viewModeButton.setFocused(false);
        }
        if (active) {
            if (RefitTransform.getCurrentTransformType() != AttachmentType.NONE) {
                RefitTransform.changeRefitScreenView(AttachmentType.NONE);
            }
            for (GuiEventListener listener : this.children()) {
                if (listener instanceof EditBox box) {
                    box.setFocused(false);
                }
            }
        }
        if (taczfixes$viewModeButton != null) {
            taczfixes$viewModeButton.setMessage(Component.translatable(
                    active ? "gui.taczfixes.refit_view.exit" : "gui.taczfixes.refit_view.enter"));
        }
    }

    @Inject(method = "m_88315_", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$viewModeRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                          CallbackInfo ci) {
        if (taczfixes$viewModeButton != null) {
            taczfixes$viewModeButton.setMessage(Component.translatable(RefitViewMode.isActive()
                    ? "gui.taczfixes.refit_view.exit" : "gui.taczfixes.refit_view.enter"));
        }
        if (taczfixes$viewModeButton != null) {
            taczfixes$viewModeButton.setFocused(false);
        }
        if (!RefitViewMode.isActive()) return;
        if (taczfixes$viewModeButton != null) {
            taczfixes$viewModeButton.render(graphics, mouseX, mouseY, partialTick);
        }
        Component hint = Component.translatable("gui.taczfixes.refit_view.hint");
        int hintX = (this.width - this.font.width(hint)) / 2;
        graphics.drawString(this.font, hint, hintX, this.height - 26, 0xFFFFFFFF, true);
        ci.cancel();
    }

    @Inject(method = "m_7379_", at = @At("TAIL"), remap = false)
    private void taczfixes$viewModeOnClose(CallbackInfo ci) {
        RefitViewMode.setActive(false);
    }
}
