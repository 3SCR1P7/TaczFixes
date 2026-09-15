package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.client.render.ClientOffhandNetworkHandler;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/network/packet/ServerMessageDualWieldEligibility.class */
public final class ServerMessageDualWieldEligibility {
    private static final int MAX_LIST_SIZE = 4096;
    private static final int MAX_VALUE_LENGTH = 512;
    private final DualWieldEligibility.Rules rules;

    public ServerMessageDualWieldEligibility(DualWieldEligibility.Rules rules) {
        this.rules = rules;
    }

    public static void encode(ServerMessageDualWieldEligibility packet, FriendlyByteBuf buffer) {
        DualWieldEligibility.Rules rules = packet.rules;
        buffer.writeBoolean(rules.allowOtherGunTypes());
        writeStrings(buffer, rules.allowedTypes());
        writeStrings(buffer, rules.allowedGuns());
        writeStrings(buffer, rules.deniedGuns());
        buffer.writeDouble(rules.dualWieldRecoilMultiplier());
        buffer.writeDouble(rules.leftGunXOffset());
        buffer.writeDouble(rules.rightGunXOffset());
    }

    public static ServerMessageDualWieldEligibility decode(FriendlyByteBuf buffer) {
        return new ServerMessageDualWieldEligibility(new DualWieldEligibility.Rules(buffer.readBoolean(), readStrings(buffer), readStrings(buffer), readStrings(buffer), buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }

    public static void handle(ServerMessageDualWieldEligibility packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> {
                return () -> {
                    ClientOffhandNetworkHandler.handleEligibility(packet.rules);
                };
            });
        });
        context.setPacketHandled(true);
    }

    private static void writeStrings(FriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size());
        for (String value : values) {
            buffer.writeUtf(value, MAX_VALUE_LENGTH);
        }
    }

    private static List<String> readStrings(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_LIST_SIZE) {
            throw new IllegalArgumentException("Invalid dual-wield eligibility list size: " + size);
        }
        List<String> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(buffer.readUtf(MAX_VALUE_LENGTH));
        }
        return values;
    }
}
