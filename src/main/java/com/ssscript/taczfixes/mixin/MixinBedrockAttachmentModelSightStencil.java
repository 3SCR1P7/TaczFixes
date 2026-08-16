package com.ssscript.taczfixes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ssscript.taczfixes.util.StencilStandbyState;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.util.RenderHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BedrockAttachmentModel.class)
public abstract class MixinBedrockAttachmentModelSightStencil {

    @WrapOperation(method = "renderSight",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/util/RenderHelper;disableItemEntityStencilTest()V", remap = false),
            remap = false)
    private void taczfixes$keepSightStencil(Operation<Void> original) {
        if (!StencilStandbyState.isActive()) {
            original.call();
            return;
        }
        RenderSystem.stencilFunc(StencilStandbyState.getFunc(),
                StencilStandbyState.getRef(), StencilStandbyState.getMask());
    }
}