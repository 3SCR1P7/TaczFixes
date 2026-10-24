package com.ssscript.taczfixes.client.mixin;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SimpleBedrockModel cancels the vanilla offhand hand render whenever the main
 * gun occupies the view. For a non-gun offhand item that also disables vanilla
 * equip/swing/bobbing state, so skip that cancellation and let vanilla render it.
 */
@Mixin(value = FirstPersonRenderHandler.class, remap = false)
public class MixinFirstPersonRenderHandlerOffhand {

    @Inject(method = "onRenderHand(Lnet/minecraftforge/client/event/RenderHandEvent;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void taczfixes$allowOffhandItem(RenderHandEvent event, CallbackInfo callback) {
        if (event.getHand() != InteractionHand.OFF_HAND) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        ItemStack mainhand = player.getMainHandItem();
        boolean mainGun = IGun.getIGunOrNull(mainhand) != null;
        boolean offGun = IGun.getIGunOrNull(offhand) != null;
        boolean mainShield = mainhand.is(net.minecraft.world.item.Items.SHIELD);
        if (mainGun && !offGun && !offhand.isEmpty()) {
            callback.cancel();
        } else if (!mainGun && offGun && !mainShield) {
            callback.cancel();
        }
    }
}
