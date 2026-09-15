package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.resource.GunDisplayInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = {GunDisplayInstance.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinGunDisplayInstanceAccessor.class */
public interface MixinGunDisplayInstanceAccessor {
    @Accessor("animationStateMachine")
    void dualWield$setAnimationStateMachine(LuaAnimationStateMachine<GunAnimationStateContext> luaAnimationStateMachine);
}
