package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.input.MeleeKey;
import com.tacz.guns.util.InputExtraCheck;
import com.ssscript.taczfixes.client.render.DualFocusAimKey;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {MeleeKey.class}, remap = false)
public abstract class MixinMeleeKey {
    @Inject(method = {"onMeleeKeyPress"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$reserveFocusAimKey(InputEvent.Key event, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && DualWieldClient.isDualMode(minecraft.player) && DualFocusAimKey.matches(event)) {
            callback.cancel();
            return;
        }
        if (!InputExtraCheck.isInGame() || event.getAction() != 1
                || !MeleeKey.MELEE_KEY.matches(event.getKey(), event.getScanCode())) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player != null && DualWieldClient.handleDualMeleeKey(player)) {
            callback.cancel();
        }
    }
}
