package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.shaders.BlendMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlendMode.class)
public interface PbrBlendModeAccessor {
    @Accessor("lastApplied")
    static BlendMode taczfixes$getLastApplied() { throw new AssertionError(); }

    @Accessor("lastApplied")
    static void taczfixes$setLastApplied(BlendMode value) { throw new AssertionError(); }
}
