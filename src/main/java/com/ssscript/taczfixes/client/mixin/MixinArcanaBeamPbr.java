package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.client.render.BeamPbrRouting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** TaCZ: Arcana 的高级镭射光束渲染同样接入 PBR 泛光。 */
@Mixin(targets = "group/taczexpands/dist/zekNTmYV", remap = false)
public class MixinArcanaBeamPbr {

    @Redirect(method = "D0LEgR5p", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = true), remap = false)
    private static VertexConsumer taczfixes$pbrBeamConsumer(MultiBufferSource source, RenderType type) {
        return BeamPbrRouting.buffer(source, type);
    }
}
