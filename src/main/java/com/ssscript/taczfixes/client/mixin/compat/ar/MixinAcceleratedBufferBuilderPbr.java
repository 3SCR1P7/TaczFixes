package com.ssscript.taczfixes.client.mixin.compat.ar;

import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.AcceleratedBufferBuilder;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.renderers.IAcceleratedRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 加速渲染路径的发光组标记。
 *
 * <p>AR 在绘制前会用 {@code doRender(renderer, context, transform, normal, light, overlay, color)}
 * 提交每个部件; 这里的 {@code light} 会被写入该次绘制的顶点数据, 着色器用 {@code UV2.x & 1} 识别。
 * 在 {@code doRender} 入口按部件(沿父链)判断 {@code *_illuminated}, 成立就把标记位 OR 进去。
 * 这条路径不依赖 compile() 的 mixin 顺序, 也不会被网格缓存/法线矩阵覆盖。</p>
 */
@Mixin(value = AcceleratedBufferBuilder.class, remap = false)
public abstract class MixinAcceleratedBufferBuilderPbr {
    @ModifyVariable(method = "doRender", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int taczfixes$markIlluminatedLight(int light, @Local(ordinal = 0) IAcceleratedRenderer<?> renderer) {
        if (!PbrRenderer.enabled() || !(renderer instanceof BedrockPart part)) {
            return light;
        }
        for (BedrockPart node = part; node != null; node = node.getParent()) {
            if (node.illuminated) {
                PbrRenderer.markEmissionSource();
                return light | 1;
            }
        }
        return light;
    }
}
