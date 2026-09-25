package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.PausableClock;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 暂停时冻结动画状态机的退出时间(墙钟改为可暂停时间)。 */
@Mixin(value = AnimationStateMachine.class, remap = false)
public class MixinAnimationStateMachinePause {

    @Redirect(method = "setExitingTime", at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"), remap = false)
    private long taczfixes$pausableNow() {
        return PausableClock.millis();
    }
}
