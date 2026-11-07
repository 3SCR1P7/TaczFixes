package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.api.client.animation.ObjectAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = {ObjectAnimation.class}, remap = false)
public interface MixinObjectAnimationAccessor {
    @Accessor(value = "maxEndTimeS", remap = false)
    void dualWield$setMaxEndTimeS(float f);
}
