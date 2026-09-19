package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.gameplay.LocalPlayerReload;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.ssscript.taczfixes.client.render.DualReloadAnimationManager;
import com.ssscript.taczfixes.client.render.DualReloadCoordinator;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {LocalPlayerReload.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinLocalPlayerReload.class */
public abstract class MixinLocalPlayerReload {
    @Inject(method = {"doReload"}, at = {@At("HEAD")})
    private void dualWield$markAcceptedMainReload(IGun gun, GunDisplayInstance display, GunData gunData, ItemStack mainHandItem, CallbackInfo callbackInfo) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && DualWieldClient.isDualMode(player)) {
            DualReloadCoordinator.markMainReloadAccepted();
        }
    }

    @Redirect(method = {"doReload"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/client/animation/statemachine/LuaAnimationStateMachine;trigger(Ljava/lang/String;)V"))
    private void dualWield$replaceReloadInput(LuaAnimationStateMachine<GunAnimationStateContext> stateMachine, String input, IGun gun, GunDisplayInstance display, GunData gunData, ItemStack mainHandItem) {
        boolean z;
        float tacticalTime;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !DualWieldClient.isDualMode(player)
                || com.ssscript.taczfixes.common.data.TaczFixesDataManager.usesNativeReloadAnimation(mainHandItem)) {
            stateMachine.trigger(input);
            return;
        }
        if (gunData.getBolt() == Bolt.OPEN_BOLT) {
            z = gun.getCurrentAmmoCount(mainHandItem) <= 0;
        } else {
            z = !gun.hasBulletInBarrel(mainHandItem);
        }
        boolean empty = z;
        if (empty) {
            tacticalTime = gunData.getReloadData().getFeed().getEmptyTime();
        } else {
            tacticalTime = gunData.getReloadData().getFeed().getTacticalTime();
        }
        float feedTime = tacticalTime;
        DualReloadAnimationManager.beginMainReload(player, mainHandItem, gunData, feedTime, stateMachine, input);
    }
}
