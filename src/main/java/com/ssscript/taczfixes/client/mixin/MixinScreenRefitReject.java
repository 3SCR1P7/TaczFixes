package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.TaczFixesClientState;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunRefitScreen.class)
public abstract class MixinScreenRefitReject extends Screen {

    protected MixinScreenRefitReject(LocalPlayer player) {
        super(Component.literal(""));
    }

    @Inject(method = "m_88315_", at = @At("HEAD"), remap = false)
    private void taczfixes$clearRejectedFocus(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (TaczFixesClientState.consumeRejectFocusClear()) {
            this.setFocused(null);
        }
    }
}