package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = {ObjectAnimationRunner.class}, remap = false)
public interface MixinObjectAnimationRunnerAccessor {
    @Accessor("running")
    boolean taczfixes$isRunning();
}