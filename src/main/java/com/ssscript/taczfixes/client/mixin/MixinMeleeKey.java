package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.input.MeleeKey;
import com.tacz.guns.util.InputExtraCheck;
import com.ssscript.taczfixes.client.client.DualFocusAimKey;
import com.ssscript.taczfixes.client.client.DualWieldClient;
import com.ssscript.taczfixes.client.client.DualWieldMelee;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {MeleeKey.class}, remap = false)
public abstract class MixinMeleeKey {
    @Inject(method = {"onMeleePress"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$reserveFocusAndMelee(CallbackInfoReturnable<Boolean> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.screen != null || !DualWieldClient.isDualMode(player)) {
            return;
        }
        if (DualFocusAimKey.FOCUS_AIM_KEY.isDown()) {
            callback.setReturnValue(true);
            return;
        }
        if (player != null && DualWieldMelee.handleDualMeleeKey(player)) {
            callback.setReturnValue(true);
        }
    }
}
