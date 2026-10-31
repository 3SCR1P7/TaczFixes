package com.ssscript.taczfixes.common.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NetworkEvent {
    public static final class Context {
        private final IPayloadContext payloadContext;

        public Context(IPayloadContext payloadContext) {
            this.payloadContext = payloadContext;
        }

        public ServerPlayer getSender() {
            return payloadContext.player() instanceof ServerPlayer player ? player : null;
        }

        public void enqueueWork(Runnable runnable) {
            payloadContext.enqueueWork(runnable);
        }

        public void setPacketHandled(boolean handled) {
        }
    }

    private NetworkEvent() {
    }
}
