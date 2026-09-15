package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.other.HumanoidOffhandRender;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {HumanoidOffhandRender.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinHumanoidOffhandRender.class */
public abstract class MixinHumanoidOffhandRender {
    @Inject(method = {"renderGun(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$suppressStoredGuns(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo callback) {
        if (DualWieldEligibility.isDualWielding(entity)) {
            callback.cancel();
        }
    }
}
