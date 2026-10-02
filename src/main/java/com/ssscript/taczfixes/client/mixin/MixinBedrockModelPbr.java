package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BedrockModel.class)
public abstract class MixinBedrockModelPbr {
    @ModifyVariable(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), argsOnly = true, remap = false)
    private RenderType taczfixes$pbr(RenderType original) {
        return PbrRenderer.resolve(original);
    }
}
