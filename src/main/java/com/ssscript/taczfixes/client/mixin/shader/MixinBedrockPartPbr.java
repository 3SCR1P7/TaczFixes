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
        if (PbrRenderer.enabled()) {
            // 沿父链判断: 自发光骨骼的子部件继承父级光照值, 也必须继承发光组标记,
            // 否则只有骨骼本身有泛光、子部件没有(加速渲染路径的 compile() 被 HEAD 取消, 父链标记只能在这里补)。
            for (BedrockPart part = self; part != null; part = part.getParent()) {
                if (part.illuminated) {
                    // 自发光骨骼/其子部件: 本帧需要泛光链路; 最低位让着色器识别这批顶点是发光组。
                    PbrRenderer.markEmissionSource();
                    packedLight = light | 1;
                    break;
                }
            }
        }
        self.compile(pose, consumer, packedLight, overlay, red, green, blue, alpha);
    }

    /**
     * 在 render 入口给"发光骨骼及其子部件"的打包光照打一个安全标记位(bit16, 落在 UV2.y 最低位)。
     * 这条注入挂在 render 上, 不会像 compile 那样被加速渲染在 HEAD 取消, 加速路径也一定生效;
     * UV2/16 做 lightmap 采样时该位会被整数除法丢掉, 不影响光照贴图。
     */
    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int taczfixes$pbrMarkRenderLight(int packedLight) {
        if (!PbrRenderer.enabled()) return packedLight;
        for (BedrockPart part = (BedrockPart) (Object) this; part != null; part = part.getParent()) {
            if (part.illuminated) {
                PbrRenderer.markEmissionSource();
                return packedLight | (1 << 16);
            }
        }
        return packedLight;
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
