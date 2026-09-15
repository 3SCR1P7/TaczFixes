package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.gameplay.LocalPlayerInspect;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.ssscript.taczfixes.client.render.DualInspectAnimationFilter;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {LocalPlayerInspect.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinLocalPlayerInspect.class */
public abstract class MixinLocalPlayerInspect {
    @Redirect(method = {"lambda$inspect$0"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/client/animation/statemachine/LuaAnimationStateMachine;trigger(Ljava/lang/String;)V"))
    private void dualWield$filterMainInspect(LuaAnimationStateMachine<GunAnimationStateContext> stateMachine, String input) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && DualWieldClient.isDualMode(player)) {
            DualInspectAnimationFilter.triggerFiltered(stateMachine, (BedrockAnimatedModel) TimelessAPI.getGunDisplay(player.getMainHandItem()).map((v0) -> {
                return v0.getGunModel();
            }).orElse(null));
        } else {
            stateMachine.trigger(input);
        }
    }
}
