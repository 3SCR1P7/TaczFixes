package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.resource.GunDisplayInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = {GunDisplayInstance.class}, remap = false)
public interface MixinGunDisplayInstanceAccessor {
    @Accessor(value = "animationStateMachine", remap = false)
    void dualWield$setAnimationStateMachine(LuaAnimationStateMachine<GunAnimationStateContext> luaAnimationStateMachine);

    @Accessor(value = "thirdPersonAnimation", remap = false)
    String dualWield$getThirdPersonAnimationRaw();

    @Accessor(value = "display", remap = false)
    com.tacz.guns.client.resource.pojo.display.gun.GunDisplay dualWield$getDisplayRaw();
}
