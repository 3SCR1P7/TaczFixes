package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = {Dist.CLIENT})
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/ClientOffhandNetworkHandler.class */
public final class ClientOffhandNetworkHandler {
    private ClientOffhandNetworkHandler() {
    }

    public static void handleState(UUID stackId, int reloadRequestId, int reloadStateType, long reloadElapsedMillis, long reloadCountDownMillis, boolean bolting, int boltRequestId, boolean boltRequestAcknowledged, boolean manualActionEpisodeActive, boolean manualActionBoltReady, boolean shootRequestAcknowledged, long acknowledgedShootTimestamp) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack currentOffhand = player.getOffhandItem();
        UUID currentStackId = DualWieldStackId.get(currentOffhand);
        if (currentStackId == null || !currentStackId.equals(stackId)) {
            return;
        }
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        boolean clientWasReloading = state.isReloading();
        state.applyServerState(reloadRequestId, reloadStateType, reloadElapsedMillis, reloadCountDownMillis, bolting, boltRequestId, boltRequestAcknowledged, manualActionEpisodeActive, manualActionBoltReady, shootRequestAcknowledged, acknowledgedShootTimestamp);
        boolean reloadStarted = state.isReloading() && !clientWasReloading;
        boolean reloadFinished = clientWasReloading && !state.isReloading();
        if (reloadStarted || bolting) {
            DualWieldClient.cancelPendingOffhandShot();
        }
        if (reloadFinished) {
            DualReloadAnimationManager.markHandReloadCompleted(InteractionHand.OFF_HAND);
        }
    }

    public static void handleActionResult(int actionType, UUID requestedStackId, int requestId, long shootTimestamp, boolean accepted, boolean stackMatched, int authoritativeFireMode, long authoritativeShootCoolDown) {
        IGun gun;
        ClientOffhandState state = OffhandDisplayManager.getClientState();
        boolean matchingFireSelect = actionType == 3 && state.isMatchingFireSelectResult(requestedStackId, requestId);
        state.applyActionResult(actionType, requestedStackId, requestId, shootTimestamp, accepted, stackMatched, authoritativeShootCoolDown);
        boolean rejected = (accepted && stackMatched) ? false : true;
        if (rejected && actionType == 0) {
            DualWieldClient.cancelOffhandShot(requestedStackId, shootTimestamp);
        }
        if ((actionType == 1 || actionType == 4) && !state.isReloading()) {
            DualReloadAnimationManager.markHandReloadCompleted(InteractionHand.OFF_HAND);
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (!matchingFireSelect || player == null) {
            return;
        }
        ItemStack currentOffhand = player.getOffhandItem();
        UUID currentStackId = DualWieldStackId.get(currentOffhand);
        FireMode[] fireModes = FireMode.values();
        if (requestedStackId.equals(currentStackId) && authoritativeFireMode >= 0 && authoritativeFireMode < fireModes.length && (gun = IGun.getIGunOrNull(currentOffhand)) != null) {
            gun.setFireMode(currentOffhand, fireModes[authoritativeFireMode]);
        }
    }

    public static void handleEligibility(DualWieldEligibility.Rules rules) {
        DualWieldEligibility.installClientRules(rules);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DualWieldEligibility.clearClientRules();
        OffhandDisplayManager.clear();
    }
}
