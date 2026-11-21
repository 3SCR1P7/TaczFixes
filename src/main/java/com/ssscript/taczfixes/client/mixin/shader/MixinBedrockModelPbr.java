package com.ssscript.taczfixes.client.mixin.shader;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BedrockModel.class)
public abstract class MixinBedrockModelPbr {
    private static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V";

    /** 本次 render 的模型里是否有自发光骨骼(补画只对可能发光的模型做)。 */
    @Unique
    private boolean taczfixes$modelIlluminated;

    @Inject(method = RENDER, at = @At("HEAD"), remap = false)
    private void taczfixes$scanModelEmission(PoseStack pose, ItemDisplayContext context, RenderType type,
                                             int light, int overlay, float red, float green, float blue, float alpha,
                                             MultiBufferSource source, CallbackInfo ci) {
        taczfixes$modelIlluminated = PbrRenderer.enabled()
                && PbrRenderer.modelHasIlluminated(((BedrockModel) (Object) this).getShouldRender());
        if (taczfixes$modelIlluminated) {
            // 加速渲染路径下补画可能被关闭, 这里在模型层标记本帧有发光来源, 保证主绘制会打开发光 MRT。
            PbrRenderer.markEmissionSource();
        }
    }

    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, remap = false)
    private RenderType taczfixes$pbr(RenderType original) {
        return PbrRenderer.resolve(original);
    }

    @Inject(method = RENDER, at = @At("RETURN"), remap = false)
    private void taczfixes$replayEmission(PoseStack pose, ItemDisplayContext context, RenderType type,
                                          int light, int overlay, float red, float green, float blue, float alpha,
                                          MultiBufferSource source, CallbackInfo ci) {
        if (PbrRenderer.isEmissionReplay() || !PbrRenderer.shouldReplayEmission(type)) {
            return;
        }
        // 不可能发光的模型(没有自发光骨骼、材质也不发光)不需要补画整遍几何。
        if (!taczfixes$modelIlluminated && !PbrRenderer.emissionPossible(type)) {
            return;
        }
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
