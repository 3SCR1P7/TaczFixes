package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.client.DualWieldClient;
import com.ssscript.taczfixes.client.util.GunRotationInertiaTransform;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.client.renderer.item.GunItemRendererWrapper", remap = false)
public abstract class MixinGunRendererRotationInertia {
    @Unique
    private boolean taczfixes$inertiaPushed;

    @Inject(method = "renderFirstPerson", at = @At("HEAD"), remap = false)
    private void taczfixes$inertiaHead(LocalPlayer player, ItemStack stack, ItemDisplayContext context,
                                       PoseStack pose, MultiBufferSource buffers, int light, float partial,
                                       CallbackInfo ci) {
        this.taczfixes$inertiaPushed = false;
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || IGun.getIGunOrNull(stack) == null
                || DualWieldClient.isDualMode(player)) {
            return;
        }
        this.taczfixes$inertiaPushed = GunRotationInertiaTransform.applyToPose(pose);
    }

    @Inject(method = "renderFirstPerson", at = @At("RETURN"), remap = false)
    private void taczfixes$inertiaReturn(LocalPlayer player, ItemStack stack, ItemDisplayContext context,
                                         PoseStack pose, MultiBufferSource buffers, int light, float partial,
                                         CallbackInfo ci) {
        if (this.taczfixes$inertiaPushed) {
            pose.popPose();
            this.taczfixes$inertiaPushed = false;
        }
    }
}
