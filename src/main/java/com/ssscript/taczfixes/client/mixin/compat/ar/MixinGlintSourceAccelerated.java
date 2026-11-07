package com.ssscript.taczfixes.client.mixin.compat.ar;

import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.IAcceleratedBufferSource;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IAcceleratableBufferSource;
import java.util.function.Supplier;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "com.ssscript.taczfixes.client.render.GunGlintContext$GlintSource", remap = false)
public abstract class MixinGlintSourceAccelerated implements IAcceleratableBufferSource {
    @Shadow(remap = false)
    private MultiBufferSource delegate;

    @Override
    public Supplier<IAcceleratedBufferSource> getBoundAcceleratedBufferSource() {
        return this.delegate instanceof IAcceleratableBufferSource acceleratable
                ? acceleratable.getBoundAcceleratedBufferSource()
                : null;
    }

    @Override
    public boolean isBufferSourceAcceleratable() {
        return this.delegate instanceof IAcceleratableBufferSource acceleratable
                && acceleratable.isBufferSourceAcceleratable();
    }

    @Override
    public void bindAcceleratedBufferSource(Supplier<IAcceleratedBufferSource> supplier) {
        if (this.delegate instanceof IAcceleratableBufferSource acceleratable) {
            acceleratable.bindAcceleratedBufferSource(supplier);
        }
    }
}
