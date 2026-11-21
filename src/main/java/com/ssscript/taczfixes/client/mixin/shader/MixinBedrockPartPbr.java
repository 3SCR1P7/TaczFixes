package com.ssscript.taczfixes.client.mixin.shader;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BedrockPart.class)
public abstract class MixinBedrockPartPbr {
    private static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V";
    private static final String COMPILE =
            "compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";

    /**
     * 自发光骨骼({@code *_illuminated})的打包光照最低位 = 自发光组标记。
     *
     * <p>加速渲染会在 {@code compile()} 的 HEAD 直接取消方法体, 只改 {@code compile()} 的入参
     * 能否生效取决于 mixin 顺序; 这里同时在调用点写标记, 加速/原版两条路径都拿得到。</p>
     */
    @Redirect(method = RENDER,
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/client/model/bedrock/BedrockPart;" + COMPILE,
                    remap = false),
            remap = false, require = 0)
    private void taczfixes$pbrMarkIlluminatedCall(BedrockPart self, PoseStack.Pose pose, VertexConsumer consumer,
                                                  int light, int overlay, float red, float green, float blue,
                                                  float alpha) {
        int packedLight = light;
        if (self.illuminated && PbrRenderer.enabled()) {
            // 自发光骨骼: 本帧需要泛光链路; 最低位让着色器识别这批顶点是发光组。
            PbrRenderer.markEmissionSource();
            packedLight = light | 1;
        }
        self.compile(pose, consumer, packedLight, overlay, red, green, blue, alpha);
    }

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
