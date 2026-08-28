package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.PosAlterGuiState;
import com.ssscript.taczfixes.client.util.RefitViewMode;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerPosAlter {

    @Inject(method = "m_91561_", at = @At("HEAD"), remap = false)
    private void taczfixes$posAlterMove(long windowPointer, double xpos, double ypos, CallbackInfo ci) {
        if (RefitViewMode.isActive()) return;
        if (!(Minecraft.getInstance().screen instanceof GunRefitScreen)) return;
        double gx = taczfixes$posToGuiX(xpos);
        double gy = taczfixes$posToGuiY(ypos);
        PosAlterGuiState.updateCursor(gx, gy);
        PosAlterGuiState.mouseDragged(gx, gy);
    }

    @Inject(method = "m_91530_", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$posAlterPress(long windowPointer, int button, int action, int mods, CallbackInfo ci) {
        if (button != 0) return;
        if (RefitViewMode.isActive()) return;
        if (!(Minecraft.getInstance().screen instanceof GunRefitScreen)) return;
        double gx = PosAlterGuiState.getCursorX();
        double gy = PosAlterGuiState.getCursorY();
        if (action == GLFW.GLFW_PRESS) {
            if (PosAlterGuiState.mouseClicked(gx, gy, 0)) {
                ci.cancel();
            }
        } else if (action == GLFW.GLFW_RELEASE) {
            if (PosAlterGuiState.mouseReleased(gx, gy, 0)) {
                ci.cancel();
            }
        }
    }

    private static double taczfixes$posToGuiX(double rawX) {
        Minecraft minecraft = Minecraft.getInstance();
        return rawX * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    private static double taczfixes$posToGuiY(double rawY) {
        Minecraft minecraft = Minecraft.getInstance();
        return rawY * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
    }
}
