package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.client.render.pbr.BeamEntityVertexConsumer;
import com.ssscript.taczfixes.client.render.pbr.PbrRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.BeamRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** 激光指示器光束: 复用枪械自发光流程(PbrType + illuminated 顶点标记)参与泛光。 */
@Mixin(value = {BeamRenderer.class}, remap = false)
public class MixinBeamRendererPbr {

    @Unique
    private static RenderType taczfixes$entityBeamType;

    @Unique
    private static boolean taczfixes$entityBeamResolved;

    @Unique
    private static RenderType taczfixes$entityBeamType() {
        if (!taczfixes$entityBeamResolved) {
            taczfixes$entityBeamResolved = true;
            try {
                var field = Class.forName("com.tacz.guns.client.model.functional.BeamRenderer$LaserBeamRenderState")
                        .getDeclaredField("LASER_BEAM_ENTITY");
                field.setAccessible(true);
                taczfixes$entityBeamType = (RenderType) field.get(null);
            } catch (ReflectiveOperationException | RuntimeException e) {
                taczfixes$entityBeamType = null;
            }
        }
        return taczfixes$entityBeamType;
    }

    /** PBR 开启时禁用 AR 加速分支, 统一走可捕获路径。 */
    @Inject(method = "renderLaserBeamAccelerated", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$disableAcceleratedBeam(ItemStack stack, PoseStack poseStack, ItemDisplayContext context, List<BedrockPart> beamPath, CallbackInfoReturnable<Boolean> cir) {
        if (PbrRenderer.enabled()) {
            cir.setReturnValue(false);
        }
    }

    /** 光束改用实体格式渲染类型并交给 PBR 合成, 顶点打上自发光标记。 */
    @Redirect(method = "renderLaserBeamUnaccelerated", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = true), require = 0, remap = false)
    private static VertexConsumer taczfixes$pbrBeamConsumer(MultiBufferSource source, RenderType type) {
        if (!PbrRenderer.enabled()) {
            return source.getBuffer(type);
        }
        RenderType entityType = taczfixes$entityBeamType();
        if (entityType == null) {
            return source.getBuffer(type);
        }
        return new BeamEntityVertexConsumer(source.getBuffer(PbrRenderer.resolve(entityType)));
    }
}
