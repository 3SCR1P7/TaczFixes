package com.ssscript.taczfixes.network;

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
    }
}
