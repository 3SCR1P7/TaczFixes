package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.AutoCraftScreenAccess;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreenAutoCraftClose {

    @Inject(method = "onClose", at = @At("TAIL"))
    private void taczfixes$requestAutoCraftStateOnClose(CallbackInfo ci) {
        if ((Object) this instanceof GunSmithTableScreen
                && (Object) this instanceof AutoCraftScreenAccess access) {
            access.taczfixes$onScreenClosed();
        }
    }
}
