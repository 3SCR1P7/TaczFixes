package com.ssscript.taczfixes.client.mixin.shader;

import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BedrockPart.class)
public abstract class MixinBedrockPartPbr {
    private static final String COMPILE =
            "compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";

    /**
     * 原版(非加速)渲染路径: 给 {@code *_illuminated} 骨骼及其子部件的打包光照置最低位,
     * 着色器用 {@code UV2.x & 1} 识别发光组。
     *
     * <p>加速渲染会在 {@code compile()} 的 HEAD 直接取消方法体, 所以加速路径改用
     * {@link MixinUnshadedVertexConsumerPbr} 写进网格几何的哨兵法线。</p>
     */
    @ModifyVariable(method = COMPILE, at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int taczfixes$pbrMarkIlluminated(int packedLight) {
        if (!PbrRenderer.enabled()) return packedLight;
        for (BedrockPart part = (BedrockPart) (Object) this; part != null; part = part.getParent()) {
            if (part.illuminated) {
                PbrRenderer.markEmissionSource();
                return packedLight | 1;
            }
        }
        return packedLight;
    }
}
