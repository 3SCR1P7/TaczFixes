package com.ssscript.taczfixes.client.mixin.pbr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BedrockModel.class)
public abstract class MixinBedrockModelPbr {
    private static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V";

    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, remap = false)
    private RenderType taczfixes$pbr(RenderType original) {
        return PbrRenderer.resolve(original);
    }

    @Inject(method = RENDER, at = @At("RETURN"), remap = false)
    private void taczfixes$replayEmission(PoseStack pose, ItemDisplayContext context, RenderType type,
                                          int light, int overlay, float red, float green, float blue, float alpha,
                                          MultiBufferSource source, CallbackInfo ci) {
        if (PbrRenderer.isEmissionReplay() || !PbrRenderer.shouldReplayEmission(type)) return;
        PbrRenderer.beginEmissionReplay();
        com.tacz.guns.compat.ar.ARCompat.disableAcceleration();
        try {
            ((BedrockModel) (Object) this).render(pose, context, type, light, overlay,
                    red, green, blue, alpha, source);
        } finally {
            com.tacz.guns.compat.ar.ARCompat.resetAcceleration();
            PbrRenderer.endEmissionReplay();
        }
    }
}
