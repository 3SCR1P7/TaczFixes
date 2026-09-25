package com.ssscript.taczfixes.client.mixin;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.RenderHandEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 双持时副手粒子由本模组的副手渲染器单独绘制, 跳过通用粒子渲染避免重复枪火。 */
@Mixin(value = FirstPersonRenderHandler.class, remap = false)
public class MixinFirstPersonRenderHandlerParticles {

    @Inject(method = "renderParticlesIfAny", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void taczfixes$skipOffhandParticlesInDual(RenderHandEvent event, CallbackInfo ci) {
        if (event.getHand() != InteractionHand.OFF_HAND) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && DualWieldClient.isDualMode(player)) {
            ci.cancel();
        }
    }
}
