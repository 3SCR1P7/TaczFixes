package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Scope bodies, rings and reticles bypass BedrockModel.render. */
@Mixin(BedrockAttachmentModel.class)
public abstract class MixinAttachmentPartPbr {
    @ModifyVariable(method = "renderTempPart(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IILjava/util/List;FLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), argsOnly = true, remap = false)
    private RenderType taczfixes$pbrAttachmentPart(RenderType original) {
        if (!PbrRenderer.enabled()) return original;
        // TaCZ also calls this method for stencil-only ocular geometry with all
        // color writes disabled. Keep that pass on its original render type.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            if (mask.get(0) == 0 && mask.get(1) == 0 && mask.get(2) == 0) return original;
        }
        // Replace the argument, not only getBuffer(): endBatch must receive the
        // same PBR render type so the part is flushed before stencil changes.
        return PbrRenderer.resolve(original);
    }
}
