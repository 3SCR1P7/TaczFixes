package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import java.util.ArrayList;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = {AnimationController.class}, remap = false)
public interface MixinAnimationControllerAccessor {
    @Accessor("prototypes")
    Map<String, ObjectAnimation> dualWield$getPrototypes();

    @Accessor("currentRunners")
    ArrayList<ObjectAnimationRunner> dualWield$getCurrentRunners();
}
