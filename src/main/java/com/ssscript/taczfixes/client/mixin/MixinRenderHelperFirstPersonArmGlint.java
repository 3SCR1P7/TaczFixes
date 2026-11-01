package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.GunGlintContext;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderHelper.class, remap = false)
public abstract class MixinRenderHelperFirstPersonArmGlint {
    private static final String RENDER_ARM =
            "renderFirstPersonArm(Lnet/minecraft/client/player/LocalPlayer;"
            + "Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;I"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;)V";

    @Inject(method = RENDER_ARM, at = @At("HEAD"), remap = false)
    private static void taczfixes$beginFirstPersonArmGlintSuppression(LocalPlayer player, HumanoidArm arm,
                                                                      PoseStack poseStack, int light,
                                                                      MultiBufferSource bufferSource,
                                                                      CallbackInfo ci) {
        GunGlintContext.suppress();
    }

    @Inject(method = RENDER_ARM, at = @At("RETURN"), remap = false)
    private static void taczfixes$endFirstPersonArmGlintSuppression(LocalPlayer player, HumanoidArm arm,
                                                                    PoseStack poseStack, int light,
                                                                    MultiBufferSource bufferSource,
                                                                    CallbackInfo ci) {
        GunGlintContext.unsuppress();
    }
}
