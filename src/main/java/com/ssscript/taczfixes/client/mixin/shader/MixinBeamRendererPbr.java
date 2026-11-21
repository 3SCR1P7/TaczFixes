package com.ssscript.taczfixes.client.mixin.shader;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.client.render.BeamPbrRouting;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.functional.BeamRenderer;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.LaserColorUtil;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 激光指示器光束(TaCZ 原生渲染路径)接入 PBR 自发光/泛光通道。
 *
 * <p>两条路径分别处理, 且都不关闭加速渲染:</p>
 * <ul>
 *   <li>加速渲染(AR)路径: 不清除加速分支, 由 {@code MixinLaserBeamRenderState} 把光束渲染类型换成
 *       PBR 变体, AR 会用 PBR 着色器绘制并顺带写出泛光光源(见 AcceleratedBufferSource 的 draw 流程);</li>
 *   <li>普通路径: 原方法写的顶点只有 pos/color/uv/light, 而 PBR 实体渲染类型还要求 overlay/normal 和
 *       "发光组"标记, 所以在 HEAD 接管, 用 {@link BeamPbrRouting} 的顶点适配器补齐元素后自己绘制。</li>
 * </ul>
 */
@Mixin(value = BeamRenderer.class, remap = false)
public class MixinBeamRendererPbr {
    private static final String UNACCELERATED_4 = "renderLaserBeamUnaccelerated("
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;)V";

    /** 可捕获路径: 用 PBR 渲染类型自己写一遍光束顶点(带发光标记), 再取消原绘制。 */
    @Inject(method = UNACCELERATED_4, at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void taczfixes$renderBeamThroughPbr(ItemStack stack, PoseStack poseStack,
                                                       ItemDisplayContext context, MultiBufferSource bufferSource,
                                                       CallbackInfo ci) {
        if (stack == null || !BeamPbrRouting.enabled()) {
            return;
        }
        if (!(context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
            return;
        }
        LaserConfig config = taczfixes$laserConfig(stack);
        int color = LaserColorUtil.getLaserColor(stack, config);
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        boolean firstPerson = context.firstPerson();
        float z = firstPerson ? -config.getLength() : -config.getLengthThird();
        float width = firstPerson ? config.getWidth() : config.getWidthThird();
        VertexConsumer consumer = BeamPbrRouting.buffer(bufferSource, BeamRenderer.LaserBeamRenderState.getLaserBeam());
        taczfixes$stringVertex(z, width, consumer, poseStack.last(), r, g, b,
                RenderConfig.ENABLE_LASER_FADE_OUT.get());
        ci.cancel();
    }

    /** 复刻 BeamRenderer#getLaserConfig(私有静态, 不能直接调用)。 */
    private static LaserConfig taczfixes$laserConfig(ItemStack stack) {
        if (stack.getItem() instanceof IAttachment attachment) {
            return TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(stack))
                    .map(ClientAttachmentIndex::getLaserConfig)
                    .orElseGet(LaserConfig::new);
        }
        if (stack.getItem() instanceof IGun) {
            return TimelessAPI.getGunDisplay(stack)
                    .map(GunDisplayInstance::getLaserConfig)
                    .orElseGet(LaserConfig::new);
        }
        return new LaserConfig();
    }

    /** 复刻 BeamRenderer#stringVertex(私有静态): 4 个交叉面片组成的光束。 */
    private static void taczfixes$stringVertex(float z, float width, VertexConsumer consumer,
                                               PoseStack.Pose pose, int r, int g, int b, boolean fadeOut) {
        float halfWidth = width / 2.0F;
        int endAlpha = fadeOut ? 0 : 255;
        int light = LightTexture.FULL_BRIGHT;
        var matrix = pose.pose();
        // 面片 1(竖)
        vertex(matrix, consumer, -halfWidth, -halfWidth, 0.0F, 0.0F, 0.0F, r, g, b, 255, light);
        vertex(matrix, consumer, -halfWidth, halfWidth, 0.0F, 0.0F, 1.0F, r, g, b, 255, light);
        vertex(matrix, consumer, -halfWidth, halfWidth, z, 1.0F, 1.0F, r, g, b, endAlpha, light);
        vertex(matrix, consumer, -halfWidth, -halfWidth, z, 1.0F, 0.0F, r, g, b, endAlpha, light);
        // 面片 2(竖的另一半)
        vertex(matrix, consumer, -halfWidth, halfWidth, 0.0F, 0.0F, 0.0F, r, g, b, 255, light);
        vertex(matrix, consumer, halfWidth, halfWidth, 0.0F, 0.0F, 1.0F, r, g, b, 255, light);
        vertex(matrix, consumer, halfWidth, halfWidth, z, 1.0F, 1.0F, r, g, b, endAlpha, light);
        vertex(matrix, consumer, -halfWidth, halfWidth, z, 1.0F, 0.0F, r, g, b, endAlpha, light);
        // 面片 3(横)
        vertex(matrix, consumer, halfWidth, halfWidth, 0.0F, 0.0F, 0.0F, r, g, b, 255, light);
        vertex(matrix, consumer, halfWidth, -halfWidth, 0.0F, 0.0F, 1.0F, r, g, b, 255, light);
        vertex(matrix, consumer, halfWidth, -halfWidth, z, 1.0F, 1.0F, r, g, b, endAlpha, light);
        vertex(matrix, consumer, halfWidth, halfWidth, z, 1.0F, 0.0F, r, g, b, endAlpha, light);
        // 面片 4(横的另一半)
        vertex(matrix, consumer, halfWidth, -halfWidth, 0.0F, 0.0F, 1.0F, r, g, b, 255, light);
        vertex(matrix, consumer, -halfWidth, -halfWidth, 0.0F, 0.0F, 1.0F, r, g, b, 255, light);
        vertex(matrix, consumer, -halfWidth, -halfWidth, z, 1.0F, 1.0F, r, g, b, endAlpha, light);
        vertex(matrix, consumer, halfWidth, -halfWidth, z, 1.0F, 0.0F, r, g, b, endAlpha, light);
    }

    private static void vertex(org.joml.Matrix4f matrix, VertexConsumer consumer, float x, float y, float z,
                               float u, float v, int r, int g, int b, int alpha, int light) {
        consumer.addVertex(matrix, x, y, z).setColor(r, g, b, alpha).setUv(u, v).setLight(light);
    }
}
