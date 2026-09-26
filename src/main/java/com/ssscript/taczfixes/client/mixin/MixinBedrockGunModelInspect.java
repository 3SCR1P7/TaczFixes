package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import com.ssscript.taczfixes.client.render.DualInspectAnimationFilter;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.client.render.OffhandDisplayManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {BedrockGunModel.class}, remap = false)
public abstract class MixinBedrockGunModelInspect {
    @Inject(method = {"render"}, at = {@At("HEAD")})
    private void dualWield$applyInspectMagazineFollower(PoseStack poseStack, ItemStack gunItem, ItemDisplayContext transformType, RenderType renderType, int light, int overlay, CallbackInfo callback) {
        DualRenderContext.HandPhase phase = DualRenderContext.getPhase();
        if (phase == DualRenderContext.HandPhase.MAIN || (phase == DualRenderContext.HandPhase.OFFHAND && OffhandDisplayManager.isActiveModel((BedrockGunModel) (Object) this))) {
            DualInspectAnimationFilter.applyRuntimeRigidFollowers((BedrockGunModel) (Object) this);
        }
        DualInspectAnimationFilter.applyThirdPersonGripTether((BedrockGunModel) (Object) this, poseStack, transformType);
    }
}
