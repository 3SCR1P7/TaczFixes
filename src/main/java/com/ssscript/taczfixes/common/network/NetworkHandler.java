package com.ssscript.taczfixes.common.network;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;


public class NetworkHandler {
    public static final SimpleChannel CHANNEL = new SimpleChannel(
            ResourceLocation.fromNamespaceAndPath("taczfixes", "main"));

    private NetworkHandler() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playBidirectional(SimpleChannel.Payload.TYPE, SimpleChannel.Payload.CODEC,
                SimpleChannel::handleIncoming);
    }

    public static void init() {
        CHANNEL.registerMessage(0, ClientMessageInstallCustomSlot.class,
                ClientMessageInstallCustomSlot::encode,
                ClientMessageInstallCustomSlot::decode,
                ClientMessageInstallCustomSlot::handle,
                true);
        CHANNEL.registerMessage(1, ClientMessageUnloadCustomSlot.class,
                ClientMessageUnloadCustomSlot::encode,
                ClientMessageUnloadCustomSlot::decode,
                ClientMessageUnloadCustomSlot::handle,
                true);
        CHANNEL.registerMessage(2, ClientMessageCustomSlotZoom.class,
                ClientMessageCustomSlotZoom::encode,
                ClientMessageCustomSlotZoom::decode,
                ClientMessageCustomSlotZoom::handle,
                true);
        CHANNEL.registerMessage(3, ClientMessageLoadRefitPreset.class,
                ClientMessageLoadRefitPreset::encode,
                ClientMessageLoadRefitPreset::decode,
                ClientMessageLoadRefitPreset::handle,
                true);
        CHANNEL.registerMessage(4, ClientMessageCustomSlotLaserColor.class,
                ClientMessageCustomSlotLaserColor::encode,
                ClientMessageCustomSlotLaserColor::decode,
                ClientMessageCustomSlotLaserColor::handle,
                true);
        CHANNEL.registerMessage(5, ClientMessageGunPosAlter.class,
                ClientMessageGunPosAlter::encode,
                ClientMessageGunPosAlter::decode,
                ClientMessageGunPosAlter::handle,
                true);
        CHANNEL.registerMessage(6, ClientMessageSetCustomSlotAdapter.class,
                ClientMessageSetCustomSlotAdapter::encode,
                ClientMessageSetCustomSlotAdapter::decode,
                ClientMessageSetCustomSlotAdapter::handle,
                true);
        CHANNEL.registerMessage(7, ClientMessageApplyGunData.class,
                ClientMessageApplyGunData::encode,
                ClientMessageApplyGunData::decode,
                ClientMessageApplyGunData::handle,
                true);
        CHANNEL.registerMessage(8, ClientMessageInputSync.class,
                ClientMessageInputSync::encode,
                ClientMessageInputSync::decode,
                ClientMessageInputSync::handle,
                true);
        CHANNEL.registerMessage(9, ClientMessageReplaceGun.class,
                ClientMessageReplaceGun::encode,
                ClientMessageReplaceGun::decode,
                ClientMessageReplaceGun::handle,
                false);
        CHANNEL.registerMessage(10, ClientMessageHoldBreath.class,
                ClientMessageHoldBreath::encode,
                ClientMessageHoldBreath::decode,
                ClientMessageHoldBreath::handle,
                true);
        CHANNEL.registerMessage(11, ServerMessageAimingStamina.class,
                ServerMessageAimingStamina::encode,
                ServerMessageAimingStamina::decode,
                ServerMessageAimingStamina::handle,
                false);
        CHANNEL.registerMessage(12, ServerMessageStamina.class,
                ServerMessageStamina::encode,
                ServerMessageStamina::decode,
                ServerMessageStamina::handle,
                false);
        CHANNEL.registerMessage(13, ClientMessageStaminaConsume.class,
                ClientMessageStaminaConsume::encode,
                ClientMessageStaminaConsume::decode,
                ClientMessageStaminaConsume::handle,
                true);
        CHANNEL.registerMessage(14, ClientMessageStaminaConsumeRaw.class,
                ClientMessageStaminaConsumeRaw::encode,
                ClientMessageStaminaConsumeRaw::decode,
                ClientMessageStaminaConsumeRaw::handle,
                true);
        CHANNEL.registerMessage(15, ClientMessageSprintState.class,
                ClientMessageSprintState::encode,
                ClientMessageSprintState::decode,
                ClientMessageSprintState::handle,
                true);
        CHANNEL.registerMessage(16, ClientMessageOffhandShoot.class,
                ClientMessageOffhandShoot::encode,
                ClientMessageOffhandShoot::decode,
                ClientMessageOffhandShoot::handle,
                true);
        CHANNEL.registerMessage(17, ClientMessageOffhandReload.class,
                ClientMessageOffhandReload::encode,
                ClientMessageOffhandReload::decode,
                ClientMessageOffhandReload::handle,
                true);
        CHANNEL.registerMessage(18, ClientMessageOffhandCancelReload.class,
                ClientMessageOffhandCancelReload::encode,
                ClientMessageOffhandCancelReload::decode,
                ClientMessageOffhandCancelReload::handle,
                true);
        CHANNEL.registerMessage(19, ClientMessageOffhandBolt.class,
                ClientMessageOffhandBolt::encode,
                ClientMessageOffhandBolt::decode,
                ClientMessageOffhandBolt::handle,
                true);
        CHANNEL.registerMessage(20, ClientMessageOffhandFireSelect.class,
                ClientMessageOffhandFireSelect::encode,
                ClientMessageOffhandFireSelect::decode,
                ClientMessageOffhandFireSelect::handle,
                true);
        CHANNEL.registerMessage(21, ServerMessageOffhandState.class,
                ServerMessageOffhandState::encode,
                ServerMessageOffhandState::decode,
                ServerMessageOffhandState::handle,
                false);
        CHANNEL.registerMessage(22, ServerMessageOffhandActionResult.class,
                ServerMessageOffhandActionResult::encode,
                ServerMessageOffhandActionResult::decode,
                ServerMessageOffhandActionResult::handle,
                false);
        CHANNEL.registerMessage(23, ServerMessageDualWieldEligibility.class,
                ServerMessageDualWieldEligibility::encode,
                ServerMessageDualWieldEligibility::decode,
                ServerMessageDualWieldEligibility::handle,
                false);
        CHANNEL.registerMessage(24, ServerMessageGunLight.class,
                ServerMessageGunLight::encode,
                ServerMessageGunLight::decode,
                ServerMessageGunLight::handle,
                false);
        CHANNEL.registerMessage(25, ClientMessageOffhandMelee.class,
                ClientMessageOffhandMelee::encode,
                ClientMessageOffhandMelee::decode,
                ClientMessageOffhandMelee::handle,
                true);
        CHANNEL.registerMessage(26, ClientMessageOffhandShootState.class,
                ClientMessageOffhandShootState::encode,
                ClientMessageOffhandShootState::decode,
                ClientMessageOffhandShootState::handle,
                true);
        CHANNEL.registerMessage(27, ClientMessageOffhandAction.class,
                ClientMessageOffhandAction::encode,
                ClientMessageOffhandAction::decode,
                ClientMessageOffhandAction::handle,
                true);
        CHANNEL.registerMessage(28, ClientMessageInstallCustomSlotBuiltin.class,
                ClientMessageInstallCustomSlotBuiltin::encode,
                ClientMessageInstallCustomSlotBuiltin::decode,
                ClientMessageInstallCustomSlotBuiltin::handle,
                true);
        CHANNEL.registerMessage(29, ClientMessageInstallVirtualAttachment.class,
                ClientMessageInstallVirtualAttachment::encode,
                ClientMessageInstallVirtualAttachment::decode,
                ClientMessageInstallVirtualAttachment::handle,
                true);
        CHANNEL.registerMessage(30, ClientMessageApplyAttachmentData.class,
                ClientMessageApplyAttachmentData::encode,
                ClientMessageApplyAttachmentData::decode,
                ClientMessageApplyAttachmentData::handle,
                true);
        CHANNEL.registerMessage(31, ClientMessageGunColor.class,
                ClientMessageGunColor::encode,
                ClientMessageGunColor::decode,
                ClientMessageGunColor::handle,
                true);
        CHANNEL.registerMessage(32, ClientMessageOffhandItemAttack.class,
                ClientMessageOffhandItemAttack::encode,
                ClientMessageOffhandItemAttack::decode,
                ClientMessageOffhandItemAttack::handle,
                true);
        CHANNEL.registerMessage(33, ClientMessageOffhandShieldState.class,
                ClientMessageOffhandShieldState::encode,
                ClientMessageOffhandShieldState::decode,
                ClientMessageOffhandShieldState::handle,
                true);
        CHANNEL.registerMessage(34, ClientMessageOffhandUseState.class,
                ClientMessageOffhandUseState::encode,
                ClientMessageOffhandUseState::decode,
                ClientMessageOffhandUseState::handle,
                true);
        CHANNEL.registerMessage(35, ClientMessageAutoCraft.class,
                ClientMessageAutoCraft::encode,
                ClientMessageAutoCraft::decode,
                ClientMessageAutoCraft::handle,
                true);
        CHANNEL.registerMessage(36, ClientMessageAutoCraftStateRequest.class,
                ClientMessageAutoCraftStateRequest::encode,
                ClientMessageAutoCraftStateRequest::decode,
                ClientMessageAutoCraftStateRequest::handle,
                true);
        CHANNEL.registerMessage(37, ServerMessageAutoCraftState.class,
                ServerMessageAutoCraftState::encode,
                ServerMessageAutoCraftState::decode,
                ServerMessageAutoCraftState::handle,
                false);
        CHANNEL.registerMessage(38, ServerMessageManualCraftPower.class,
                ServerMessageManualCraftPower::encode,
                ServerMessageManualCraftPower::decode,
                ServerMessageManualCraftPower::handle,
                false);
        CHANNEL.registerMessage(39, ServerMessageContraptionHit.class,
                ServerMessageContraptionHit::encode,
                ServerMessageContraptionHit::decode,
                ServerMessageContraptionHit::handle,
                false);

    }
}
