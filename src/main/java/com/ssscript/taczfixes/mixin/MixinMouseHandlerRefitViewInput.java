package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.RefitViewMode;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerRefitViewInput {

    @Inject(method = "m_91530_", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$viewModePress(long windowPointer, int button, int action, int mods, CallbackInfo ci) {
        if (!RefitViewMode.isActive()) return;
        if (!(Minecraft.getInstance().screen instanceof GunRefitScreen)) return;
        if (button == 0) {
            if (action == GLFW.GLFW_PRESS) {
                double mx = RefitViewMode.getCursorX();
                double my = RefitViewMode.getCursorY();
                if (RefitViewMode.hitButton(mx, my)) {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    RefitViewMode.setActive(false);
                    unfocusEditBox();
                    ci.cancel();
                } else {
                    RefitViewMode.beginDrag(mx, my);
                    ci.cancel();
                }
            } else if (action == GLFW.GLFW_RELEASE) {
                if (RefitViewMode.isDragging()) {
                    RefitViewMode.endDrag();
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "m_91561_", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$viewModeMove(long windowPointer, double xpos, double ypos, CallbackInfo ci) {
        if (!RefitViewMode.isActive()) return;
        if (!(Minecraft.getInstance().screen instanceof GunRefitScreen)) return;
        double gx = toGuiX(xpos);
        double gy = toGuiY(ypos);
        RefitViewMode.updateCursor(gx, gy);
        if (RefitViewMode.isDragging()) {
            RefitViewMode.dragTo(gx, gy);
            ci.cancel();
        }
    }

    @Inject(method = "m_91526_", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$viewModeScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        if (!RefitViewMode.isActive()) return;
        if (!(Minecraft.getInstance().screen instanceof GunRefitScreen)) return;
        RefitViewMode.addScroll(yOffset);
        ci.cancel();
    }

    private static double toGuiX(double rawX) {
        Minecraft minecraft = Minecraft.getInstance();
        return rawX * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    private static double toGuiY(double rawY) {
        Minecraft minecraft = Minecraft.getInstance();
        return rawY * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
    }

    private static void unfocusEditBox() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return;
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof EditBox box) {
                box.setFocused(false);
            }
        }
    }
}