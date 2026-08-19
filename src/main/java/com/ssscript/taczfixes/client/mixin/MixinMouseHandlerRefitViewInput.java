package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.RefitViewMode;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.AbstractWidget;
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

    private static final String CHARM_BUTTON_CLASS = "com.VvvV.taczcharms.client.gui.CharmPaletteButton";
    private static final String CHARM_PICKER_CLASS = "com.VvvV.taczcharms.client.gui.CharmInventoryPicker";
    private static final String CHARM_ANCHOR_CLASS = "com.VvvV.taczcharms.client.gui.CharmAnchorSelection";
    private static java.lang.reflect.Field taczfixes$pickerExpanded;
    private static java.lang.reflect.Method taczfixes$pickerPanelX;
    private static java.lang.reflect.Method taczfixes$pickerPanelBottom;
    private static java.lang.reflect.Method taczfixes$anchorActive;

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
                } else if (taczfixes$passToScreen(mx, my)) {
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
        } else if (button == 1) {
            if (action == GLFW.GLFW_PRESS) {
                double mx = RefitViewMode.getCursorX();
                double my = RefitViewMode.getCursorY();
                if (taczfixes$passToScreen(mx, my)) {
                } else {
                    if (!RefitViewMode.hitButton(mx, my)) {
                        RefitViewMode.beginPan(mx, my);
                    }
                    ci.cancel();
                }
            } else if (action == GLFW.GLFW_RELEASE) {
                if (RefitViewMode.isPanning()) {
                    RefitViewMode.endPan();
                }
                ci.cancel();
            }
        }
    }

    @Inject(method = "m_91526_", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$viewModeScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        if (!RefitViewMode.isActive()) return;
        if (!(Minecraft.getInstance().screen instanceof GunRefitScreen)) return;
        if (taczfixes$pickerExpanded()) return;
        RefitViewMode.addScroll(yOffset);
        ci.cancel();
    }

    private static boolean taczfixes$passToScreen(double mx, double my) {
        if (taczfixes$hitCharmButton(mx, my)) return true;
        if (taczfixes$pickerExpanded() && taczfixes$inPickerPanel(mx, my)) return true;
        return taczfixes$anchorActive();
    }

    private static boolean taczfixes$hitCharmButton(double mx, double my) {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof GunRefitScreen)) return false;
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractWidget widget
                    && widget.getClass().getName().equals(CHARM_BUTTON_CLASS)
                    && mx >= widget.getX() && mx <= widget.getX() + widget.getWidth()
                    && my >= widget.getY() && my <= widget.getY() + widget.getHeight()) {
                return true;
            }
        }
        return false;
    }

    private static void taczfixes$initCharmReflection() {
        if (taczfixes$pickerExpanded != null) return;
        try {
            Class<?> picker = Class.forName(CHARM_PICKER_CLASS);
            taczfixes$pickerExpanded = picker.getDeclaredField("expanded");
            taczfixes$pickerExpanded.setAccessible(true);
            taczfixes$pickerPanelX = picker.getDeclaredMethod("panelX", int.class);
            taczfixes$pickerPanelX.setAccessible(true);
            taczfixes$pickerPanelBottom = picker.getDeclaredMethod("panelBottom", int.class);
            taczfixes$pickerPanelBottom.setAccessible(true);
            Class<?> anchor = Class.forName(CHARM_ANCHOR_CLASS);
            taczfixes$anchorActive = anchor.getMethod("active");
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static boolean taczfixes$pickerExpanded() {
        taczfixes$initCharmReflection();
        try {
            return taczfixes$pickerExpanded != null && taczfixes$pickerExpanded.getBoolean(null);
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    private static boolean taczfixes$anchorActive() {
        taczfixes$initCharmReflection();
        try {
            return taczfixes$anchorActive != null && (Boolean) taczfixes$anchorActive.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean taczfixes$inPickerPanel(double mx, double my) {
        taczfixes$initCharmReflection();
        try {
            if (taczfixes$pickerPanelX == null) return false;
            Screen screen = Minecraft.getInstance().screen;
            if (!(screen instanceof GunRefitScreen refitScreen)) return false;
            int panelX = (int) taczfixes$pickerPanelX.invoke(null, refitScreen.width);
            int panelBottom = (int) taczfixes$pickerPanelBottom.invoke(null, refitScreen.height);
            return mx >= panelX && mx <= panelX + 198 && my >= panelBottom - 158 && my <= panelBottom;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

@Inject(method = "m_91561_", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$viewModeMove(long windowPointer, double xpos, double ypos, CallbackInfo ci) {
        if (!RefitViewMode.isActive()) return;
        if (!(Minecraft.getInstance().screen instanceof GunRefitScreen)) return;
        double gx = taczfixes$refitToGuiX(xpos);
        double gy = taczfixes$refitToGuiY(ypos);
        RefitViewMode.updateCursor(gx, gy);
        if (RefitViewMode.isDragging() || RefitViewMode.isPanning()) {
            RefitViewMode.dragTo(gx, gy);
            RefitViewMode.dragToPan(gx, gy);
            ci.cancel();
        }
    }

    private static double taczfixes$refitToGuiX(double rawX) {
        Minecraft minecraft = Minecraft.getInstance();
        return rawX * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    private static double taczfixes$refitToGuiY(double rawY) {
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