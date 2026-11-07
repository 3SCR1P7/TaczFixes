package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.event.TickAnimationEvent;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(value = {TickAnimationEvent.class}, remap = false)
public abstract class MixinTickAnimationEvent {
    @ModifyArg(method = {"lambda$tickAnimation$0(Lnet/minecraft/client/player/LocalPlayer;Lcom/tacz/guns/client/resource/GunDisplayInstance;)V"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/client/animation/statemachine/LuaAnimationStateMachine;trigger(Ljava/lang/String;)V", remap = false), index = ServerMessageOffhandActionResult.ACTION_SHOOT, require = ServerMessageOffhandActionResult.ACTION_CANCEL_RELOAD, allow = ServerMessageOffhandActionResult.ACTION_CANCEL_RELOAD, remap = false)
    private static String dualWield$useThirdPersonSprintPose(String input) {
        String resolved = input;
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson() && "run".equals(input)) {
            resolved = "idle";
        }
        return com.ssscript.taczfixes.client.client.MainhandMovementAnimation.adjustDualMovementTrigger(resolved, Minecraft.getInstance().player);
    }
}
