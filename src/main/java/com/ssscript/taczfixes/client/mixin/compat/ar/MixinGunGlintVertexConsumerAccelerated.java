package com.ssscript.taczfixes.client.mixin.compat.ar;

import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IAcceleratedVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.client.render.GunGlintVertexConsumer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = GunGlintVertexConsumer.class, remap = false)
public abstract class MixinGunGlintVertexConsumerAccelerated implements IAcceleratedVertexConsumer {
    @Override
    public VertexConsumer decorate(VertexConsumer consumer) {
        return consumer;
    }

    @Override
    public boolean isAccelerated() {
        return false;
    }
}
