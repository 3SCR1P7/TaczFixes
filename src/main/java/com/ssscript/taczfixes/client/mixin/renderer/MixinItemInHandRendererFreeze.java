package com.ssscript.taczfixes.client.mixin.renderer;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 常凪冻结: 冻结自己时不再渲染第一人称手/枪模型, 避免视角画面里的模型抽搐。 */
@Mixin(ItemInHandRenderer.class)
public class MixinItemInHandRendererFreeze {
    @Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V",
            at = @At("HEAD"), cancellable = true)
    private void taczfixes$skipFrozenHands(float partialTick, PoseStack poseStack,
                                           MultiBufferSource.BufferSource bufferSource,
                                           LocalPlayer player, int light, CallbackInfo ci) {
        if (FreezeClientState.isFrozen()) {
            ci.cancel();
        }
    }
}
