package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.api.client.animation.ObjectAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = {ObjectAnimation.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinObjectAnimationAccessor.class */
public interface MixinObjectAnimationAccessor {
    @Accessor("maxEndTimeS")
    void dualWield$setMaxEndTimeS(float f);
}
