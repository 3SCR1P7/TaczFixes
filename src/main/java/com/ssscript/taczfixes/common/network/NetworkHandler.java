package com.ssscript.taczfixes.common.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class NetworkHandler {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("taczfixes", "main"),
            () -> "1",
            "1"::equals,
            "1"::equals);

    private NetworkHandler() {
    }

    public static void init() {
        CHANNEL.registerMessage(0, ClientMessageInstallCustomSlot.class,
                ClientMessageInstallCustomSlot::encode,
                ClientMessageInstallCustomSlot::decode,
                ClientMessageInstallCustomSlot::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, ClientMessageUnloadCustomSlot.class,
                ClientMessageUnloadCustomSlot::encode,
                ClientMessageUnloadCustomSlot::decode,
                ClientMessageUnloadCustomSlot::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, ClientMessageCustomSlotZoom.class,
                ClientMessageCustomSlotZoom::encode,
                ClientMessageCustomSlotZoom::decode,
                ClientMessageCustomSlotZoom::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, ClientMessageLoadRefitPreset.class,
                ClientMessageLoadRefitPreset::encode,
                ClientMessageLoadRefitPreset::decode,
                ClientMessageLoadRefitPreset::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, ClientMessageCustomSlotLaserColor.class,
                ClientMessageCustomSlotLaserColor::encode,
                ClientMessageCustomSlotLaserColor::decode,
                ClientMessageCustomSlotLaserColor::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5, ClientMessageGunPosAlter.class,
                ClientMessageGunPosAlter::encode,
                ClientMessageGunPosAlter::decode,
                ClientMessageGunPosAlter::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, ClientMessageSetCustomSlotAdapter.class,
                ClientMessageSetCustomSlotAdapter::encode,
                ClientMessageSetCustomSlotAdapter::decode,
                ClientMessageSetCustomSlotAdapter::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(7, ClientMessageApplyGunData.class,
                ClientMessageApplyGunData::encode,
                ClientMessageApplyGunData::decode,
                ClientMessageApplyGunData::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(8, ClientMessageInputSync.class,
                ClientMessageInputSync::encode,
                ClientMessageInputSync::decode,
                ClientMessageInputSync::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(9, ClientMessageReplaceGun.class,
                ClientMessageReplaceGun::encode,
                ClientMessageReplaceGun::decode,
                ClientMessageReplaceGun::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(10, ClientMessageHoldBreath.class,
                ClientMessageHoldBreath::encode,
                ClientMessageHoldBreath::decode,
                ClientMessageHoldBreath::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(11, ServerMessageAimingStamina.class,
                ServerMessageAimingStamina::encode,
                ServerMessageAimingStamina::decode,
                ServerMessageAimingStamina::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(12, ServerMessageStamina.class,
                ServerMessageStamina::encode,
                ServerMessageStamina::decode,
                ServerMessageStamina::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(13, ClientMessageStaminaConsume.class,
                ClientMessageStaminaConsume::encode,
                ClientMessageStaminaConsume::decode,
                ClientMessageStaminaConsume::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(14, ClientMessageStaminaConsumeRaw.class,
                ClientMessageStaminaConsumeRaw::encode,
                ClientMessageStaminaConsumeRaw::decode,
                ClientMessageStaminaConsumeRaw::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(15, ClientMessageSprintState.class,
                ClientMessageSprintState::encode,
                ClientMessageSprintState::decode,
                ClientMessageSprintState::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
