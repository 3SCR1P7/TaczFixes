package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.event.TickAnimationEvent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = {TickAnimationEvent.class}, remap = false)
public abstract class MixinTickAnimationEvent {
    // 不把业务常量当 require/allow 用: 原先"恰好 4 处匹配"才会应用, TaCZ 改动 trigger 调用数量就会启动失败。
    // index 固定为 0(trigger 的字符串参数), 匹配全部同类调用, 处理器内部按输入字符串决定是否改写。
    @ModifyArg(method = {"lambda$tickAnimation$0(Lnet/minecraft/client/player/LocalPlayer;Lcom/tacz/guns/client/resource/GunDisplayInstance;)V"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/client/animation/statemachine/LuaAnimationStateMachine;trigger(Ljava/lang/String;)V", remap = false), index = 0, remap = false)
    private static String dualWield$useThirdPersonSprintPose(String input) {
        String resolved = input;
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson() && "run".equals(input)) {
            resolved = "idle";
        }
        return com.ssscript.taczfixes.client.client.MainhandMovementAnimation.adjustDualMovementTrigger(resolved, Minecraft.getInstance().player);
    }
}
