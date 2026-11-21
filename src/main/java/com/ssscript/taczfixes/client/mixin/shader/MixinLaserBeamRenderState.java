package com.ssscript.taczfixes.client.mixin.shader;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.ssscript.taczfixes.client.render.BeamPbrRouting;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 激光指示器的加速渲染(AR)路径接入 PBR 泛光。
 *
 * <p>TaCZ 的加速分支从 {@code getLaserBeamEntity()} 取渲染类型再交给 Accelerated Rendering:
 * 这里换成 PBR 变体, AR 会在 draw 流程里调用该 RenderType 的 setupRenderState/clearRenderState,
 * 于是 PBR 着色器与发光附件写入都照常生效 —— 不需要关闭加速分支, 也不会丢掉 AR 的性能收益。
 * 光束顶点的"发光组"标记由 {@code MixinAcceleratedBufferBuilderPbr} 在 doRender 里补上。</p>
 */
@Mixin(targets = "com.tacz.guns.client.model.functional.BeamRenderer$LaserBeamRenderState", remap = false)
public class MixinLaserBeamRenderState {
    @ModifyReturnValue(method = "getLaserBeamEntity", at = @At("RETURN"), remap = false)
    private static RenderType taczfixes$pbrLaserBeamEntity(RenderType original) {
        if (original == null || !BeamPbrRouting.enabled()) {
            return original;
        }
        PbrRenderer.markEmissionSource();
        return PbrRenderer.resolve(original);
    }
}
