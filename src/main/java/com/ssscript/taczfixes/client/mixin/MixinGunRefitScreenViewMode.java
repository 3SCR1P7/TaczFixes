package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.RefitViewMode;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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

import java.lang.reflect.Method;

@Mixin(GunRefitScreen.class)
public abstract class MixinGunRefitScreenViewMode extends Screen {

    private static final String CHARM_BUTTON_CLASS = "com.VvvV.taczcharms.client.gui.CharmPaletteButton";
    private static final String CHARM_PICKER_CLASS = "com.VvvV.taczcharms.client.gui.CharmInventoryPicker";
    private static final String CHARM_ANCHOR_CLASS = "com.VvvV.taczcharms.client.gui.CharmAnchorSelection";
    private static Method taczfixes$pickerRender;
    private static Method taczfixes$pickerReset;
    private static Method taczfixes$anchorHover;
    private static Method taczfixes$anchorOverlay;

    protected MixinGunRefitScreenViewMode(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Unique
    private Button taczfixes$viewModeButton;

    @Inject(method = "addAttachmentTypeButtons", at = @At("TAIL"), remap = false)
    private void taczfixes$ensureViewModeButton(CallbackInfo ci) {
        if (!com.ssscript.taczfixes.common.register.Config.REFITSCREEN_SHOW_VIEW_BUTTON.get()) {
            return;
        }
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
        Button button = new com.ssscript.taczfixes.client.util.TransparentButton(label,
                b -> taczfixes$toggleViewMode()).bounds(x, 10, width, 18);
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
        boolean active = RefitViewMode.isActive();
        taczfixes$syncCharmButtons(active);
        if (!active) {
            taczfixes$renderRefitPoint(graphics);
            return;
        }
        if (taczfixes$viewModeButton != null) {
            taczfixes$viewModeButton.render(graphics, mouseX, mouseY, partialTick);
        }
        taczfixes$renderCharmUi(graphics, mouseX, mouseY);
        Component hint = Component.translatable("gui.taczfixes.refit_view.hint");
        int hintX = (this.width - this.font.width(hint)) / 2;
        graphics.drawString(this.font, hint, hintX, this.height - 26, 0xFFFFFFFF, true);
        ci.cancel();
    }

    @Unique
    private void taczfixes$renderRefitPoint(GuiGraphics graphics) {
        if (taczfixes$viewModeButton == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        Integer total = TaczFixesDataManager.getGunRefitPoint(gunStack);
        if (total == null) return;
        int used = AttachmentTaczFixesManager.getRefitPointUsed(gunStack);
        Component text = Component.literal(used + "/" + total);
        int x = taczfixes$viewModeButton.getX() - this.font.width(text) - 6;
        int y = taczfixes$viewModeButton.getY() + (taczfixes$viewModeButton.getHeight() - this.font.lineHeight) / 2;
        graphics.drawString(this.font, text, x, y, 0xFFFFFFFF, true);
    }

    @Unique
    private void taczfixes$syncCharmButtons(boolean charmMode) {
        for (GuiEventListener listener : this.children()) {
            if (listener instanceof AbstractWidget widget
                    && widget != taczfixes$viewModeButton) {
                boolean charm = widget.getClass().getName().equals(CHARM_BUTTON_CLASS);
                widget.visible = charmMode == charm;
                widget.active = charmMode == charm;
            }
        }
        if (!charmMode) {
            taczfixes$resetCharmPicker();
        }
    }

    @Unique
    private void taczfixes$renderCharmUi(GuiGraphics graphics, int mouseX, int mouseY) {
        taczfixes$initCharmReflection();
        try {
            if (taczfixes$anchorHover != null) {
                taczfixes$anchorHover.invoke(null, (double) mouseX, (double) mouseY,
                        this.width, this.height);
            }
            for (GuiEventListener listener : this.children()) {
                if (listener instanceof AbstractWidget widget
                        && widget.getClass().getName().equals(CHARM_BUTTON_CLASS)) {
                    widget.render(graphics, mouseX, mouseY, 1.0f);
                }
            }
            if (taczfixes$pickerRender != null) {
                taczfixes$pickerRender.invoke(null, graphics, this.width, this.height, mouseX, mouseY);
            }
            if (taczfixes$anchorOverlay != null) {
                taczfixes$anchorOverlay.invoke(null, graphics);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Unique
    private static void taczfixes$initCharmReflection() {
        if (taczfixes$pickerRender != null) return;
        try {
            Class<?> picker = Class.forName(CHARM_PICKER_CLASS);
            taczfixes$pickerRender = picker.getMethod("render", GuiGraphics.class,
                    int.class, int.class, int.class, int.class);
            taczfixes$pickerReset = picker.getMethod("reset");
            Class<?> anchor = Class.forName(CHARM_ANCHOR_CLASS);
            taczfixes$anchorHover = anchor.getMethod("updateHover", double.class,
                    double.class, int.class, int.class);
            taczfixes$anchorOverlay = anchor.getMethod("renderOverlay", GuiGraphics.class);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Unique
    private static void taczfixes$resetCharmPicker() {
        taczfixes$initCharmReflection();
        try {
            if (taczfixes$pickerReset != null) {
                taczfixes$pickerReset.invoke(null);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Inject(method = "m_7379_", at = @At("TAIL"), remap = false)
    private void taczfixes$viewModeOnClose(CallbackInfo ci) {
        RefitViewMode.setActive(false);
    }
}
