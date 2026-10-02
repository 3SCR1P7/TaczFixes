package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.common.config.Config;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** 镭射光束接入 PBR 泛光的公共路由: TaCZ 原版与 Arcana 高级光束共用。 */
public final class BeamPbrRouting {
    private static RenderType entityBeamType;
    private static boolean entityBeamResolved;

    private BeamPbrRouting() {
    }

    public static boolean enabled() {
        return PbrRenderer.enabled() && Config.PBR_BLOOM_LASER.get();
    }

    public static VertexConsumer buffer(MultiBufferSource source, RenderType original) {
        if (!enabled()) {
            return source.getBuffer(original);
        }
        RenderType entityType = entityBeamType();
        if (entityType == null) {
            return source.getBuffer(original);
        }
        PbrBloom.markEmissive();
        return new BeamEntityVertexConsumer(source.getBuffer(PbrRenderer.resolve(entityType)));
    }

    private static RenderType entityBeamType() {
        if (!entityBeamResolved) {
            entityBeamResolved = true;
            try {
                var field = Class.forName("com.tacz.guns.client.model.functional.BeamRenderer$LaserBeamRenderState")
                        .getDeclaredField("LASER_BEAM_ENTITY");
                field.setAccessible(true);
                entityBeamType = (RenderType) field.get(null);
            } catch (ReflectiveOperationException | RuntimeException e) {
                entityBeamType = null;
            }
        }
        return entityBeamType;
    }
}
