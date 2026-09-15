package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.config.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 启用本模组耐力系统时隐藏 ParCool 的耐力条。 */
@Mixin(targets = "com.alrex.parcool.client.hud.impl.StaminaHUDController", remap = false)
public class MixinParCoolStaminaHud {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$hideHud(ForgeGui gui, GuiGraphics graphics, float partialTick,
                                   int width, int height, CallbackInfo ci) {
        if (Config.STAMINA_ENABLED.get()) {
            ci.cancel();
        }
    }
}
