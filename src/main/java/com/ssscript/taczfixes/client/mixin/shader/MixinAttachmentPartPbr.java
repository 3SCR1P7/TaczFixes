package com.ssscript.taczfixes.client.mixin.shader;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BedrockAttachmentModel.class)
public abstract class MixinAttachmentPartPbr {
    private static final String RENDER_TEMP_PART_8 =
            "renderTempPart(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lnet/minecraft/client/renderer/RenderType;IILjava/util/List;F"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;)V";
    private static final String RENDER_TEMP_PART_6 =
            "renderTempPart(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lnet/minecraft/client/renderer/RenderType;IILjava/util/List;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;)V";

    @Invoker("renderTempPart")
    abstract void taczfixes$renderTempPart8(PoseStack pose, ItemDisplayContext context, RenderType type,
                                            int light, int overlay, List<BedrockPart> path, float partialTick,
                                            MultiBufferSource source);

    @Invoker("renderTempPart")
    abstract void taczfixes$renderTempPart6(PoseStack pose, ItemDisplayContext context, RenderType type,
                                            int light, int overlay, List<BedrockPart> path,
                                            MultiBufferSource source);

    @ModifyVariable(method = {RENDER_TEMP_PART_8, RENDER_TEMP_PART_6},
            at = @At("HEAD"), argsOnly = true, remap = false)
    private RenderType taczfixes$pbrAttachmentPart(RenderType original) {
        if (!PbrRenderer.enabled()) return original;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            if (mask.get(0) == 0 && mask.get(1) == 0 && mask.get(2) == 0) return original;
        }
        return PbrRenderer.resolve(original);
    }

    @Inject(method = RENDER_TEMP_PART_8, at = @At("RETURN"), remap = false)
    private void taczfixes$replayEmission8(PoseStack pose, ItemDisplayContext context, RenderType type,
                                           int light, int overlay, List<?> path, float partialTick,
                                           MultiBufferSource source, CallbackInfo ci) {
        if (PbrRenderer.isEmissionReplay() || !PbrRenderer.shouldReplayEmission(type)) return;
        PbrRenderer.beginEmissionReplay();
        com.tacz.guns.compat.ar.ARCompat.disableAcceleration();
        try {
            taczfixes$renderTempPart8(pose, context, type, light, overlay, (List<BedrockPart>) path, partialTick, source);
        } finally {
            com.tacz.guns.compat.ar.ARCompat.resetAcceleration();
            PbrRenderer.endEmissionReplay();
        }
    }

    @Inject(method = RENDER_TEMP_PART_6, at = @At("RETURN"), remap = false)
    private void taczfixes$replayEmission6(PoseStack pose, ItemDisplayContext context, RenderType type,
                                           int light, int overlay, List<?> path,
                                           MultiBufferSource source, CallbackInfo ci) {
        if (PbrRenderer.isEmissionReplay() || !PbrRenderer.shouldReplayEmission(type)) return;
        PbrRenderer.beginEmissionReplay();
        com.tacz.guns.compat.ar.ARCompat.disableAcceleration();
        try {
            taczfixes$renderTempPart6(pose, context, type, light, overlay, (List<BedrockPart>) path, source);
        } finally {
            com.tacz.guns.compat.ar.ARCompat.resetAcceleration();
            PbrRenderer.endEmissionReplay();
        }
    }
}
