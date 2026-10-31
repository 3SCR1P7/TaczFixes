package com.ssscript.taczfixes.common.network;

import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class SimpleChannel {
    private static SimpleChannel instance;

    private final ResourceLocation channelName;
    private final Map<Class<?>, Entry> byClass = new HashMap<>();
    private final Map<ResourceLocation, Entry> byType = new HashMap<>();

    public SimpleChannel(ResourceLocation channelName) {
        this.channelName = channelName;
        instance = this;
    }

    public <T> void registerMessage(int id, Class<T> type,
                                    BiConsumer<T, FriendlyByteBuf> encoder,
                                    Function<FriendlyByteBuf, T> decoder,
                                    BiConsumer<T, Supplier<NetworkEvent.Context>> handler,
                                    boolean serverbound) {
        ResourceLocation messageId = ResourceLocation.fromNamespaceAndPath(channelName.getNamespace(),
                channelName.getPath() + "/" + id);
        Entry entry = new Entry(messageId, type, encoder, decoder, handler, serverbound);
        byClass.put(type, entry);
        byType.put(messageId, entry);
    }

    public void sendToServer(Object message) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(encode(message));
    }

    public void send(PacketDistributor.Player target, Object message) {
        ServerPlayer player = target.supplier().get();
        if (player != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, encode(message));
        }
    }

    public void send(PacketDistributor.Near target, Object message) {
        PacketDistributor.TargetPoint point = target.supplier().get();
        if (point == null) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.getLevel(point.dimension());
        if (level != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(level, null,
                    point.x(), point.y(), point.z(), point.radius(), encode(message));
        }
    }

    private Payload encode(Object message) {
        Entry entry = byClass.get(message.getClass());
        if (entry == null) {
            throw new IllegalArgumentException("Unregistered message " + message.getClass());
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            entry.encode(message, buf);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new Payload(entry.messageId, data);
        } finally {
            buf.release();
        }
    }

    static void handleIncoming(Payload payload, IPayloadContext context) {
        SimpleChannel channel = instance;
        if (channel == null) {
            return;
        }
        Entry entry = channel.byType.get(payload.messageId());
        if (entry == null) {
            return;
        }
        boolean serverbound = context.flow().isServerbound();
        if (serverbound != entry.serverbound) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
        try {
            Object message = entry.decode(buf);
            NetworkEvent.Context eventContext = new NetworkEvent.Context(context);
            entry.handle(message, () -> eventContext);
        } finally {
            buf.release();
        }
    }

    public record Payload(ResourceLocation messageId, byte[] data) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("taczfixes", "main"));
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeResourceLocation(payload.messageId());
                    buf.writeByteArray(payload.data());
                },
                buf -> new Payload(buf.readResourceLocation(), buf.readByteArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final class Entry {
        private final ResourceLocation messageId;
        private final Class<?> type;
        private final BiConsumer<Object, FriendlyByteBuf> encoder;
        private final Function<FriendlyByteBuf, Object> decoder;
        private final BiConsumer<Object, Supplier<NetworkEvent.Context>> handler;
        private final boolean serverbound;

        @SuppressWarnings("unchecked")
        <T> Entry(ResourceLocation messageId, Class<T> type,
                  BiConsumer<T, FriendlyByteBuf> encoder,
                  Function<FriendlyByteBuf, T> decoder,
                  BiConsumer<T, Supplier<NetworkEvent.Context>> handler,
                  boolean serverbound) {
            this.messageId = messageId;
            this.type = type;
            this.encoder = (BiConsumer<Object, FriendlyByteBuf>) encoder;
            this.decoder = (Function<FriendlyByteBuf, Object>) decoder;
            this.handler = (BiConsumer<Object, Supplier<NetworkEvent.Context>>) handler;
            this.serverbound = serverbound;
        }

        private void encode(Object message, FriendlyByteBuf buf) {
            encoder.accept(message, buf);
        }

        private Object decode(FriendlyByteBuf buf) {
            return decoder.apply(buf);
        }

        private void handle(Object message, Supplier<NetworkEvent.Context> context) {
            handler.accept(message, context);
        }
    }
}