package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.PbrBloom;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BedrockPart.class)
public abstract class MixinBedrockPartPbr {
    @ModifyVariable(method = "compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int taczfixes$pbrMarkIlluminated(int packedLight) {
        if (!PbrRenderer.enabled()) return packedLight;
        // Mark the light passed to the actual cubes, including direct compile calls.
        // Preserve TaCZ's flag and inherited illumination without changing its consumer type.
        for (BedrockPart part = (BedrockPart) (Object) this; part != null; part = part.getParent()) {
            if (part.illuminated) {
                PbrBloom.markEmissive();
                return packedLight | 1;
            }
        }
        return packedLight;
    }
}
