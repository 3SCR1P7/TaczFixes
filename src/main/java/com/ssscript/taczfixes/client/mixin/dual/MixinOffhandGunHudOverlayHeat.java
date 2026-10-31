package com.ssscript.taczfixes.client.mixin.dual;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.ssscript.taczfixes.client.client.DualOffhandHeatBar;
import com.ssscript.taczfixes.client.client.OffhandGunHudOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {OffhandGunHudOverlay.class}, remap = false)
public abstract class MixinOffhandGunHudOverlayHeat {
    @Inject(method = {"renderHeat"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private static void dualWield$removeCustomOffhandHeatBar(GuiGraphics graphics, ItemStack stack, IGun gun, GunData gunData, int height, CallbackInfo callback) {
        callback.cancel();
    }

    @Inject(method = {"render"}, at = {@At("TAIL")}, remap = false)
    private void dualWield$renderNativeOffhandHeatBar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        DualOffhandHeatBar.render(graphics, graphics.guiWidth(), graphics.guiHeight());
    }
}
