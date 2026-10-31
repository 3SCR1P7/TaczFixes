package com.ssscript.taczfixes.common.network;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class PacketDistributor {
    public static final PlayerTarget PLAYER = new PlayerTarget();
    public static final NearTarget NEAR = new NearTarget();

    public record TargetPoint(double x, double y, double z, double radius, ResourceKey<Level> dimension) {
    }

    public record Player(Supplier<ServerPlayer> supplier) {
    }

    public record Near(Supplier<TargetPoint> supplier) {
    }

    public static final class PlayerTarget {
        private PlayerTarget() {
        }

        public Player with(Supplier<ServerPlayer> supplier) {
            return new Player(supplier);
        }
    }

    public static final class NearTarget {
        private NearTarget() {
        }

        public Near with(Supplier<TargetPoint> supplier) {
            return new Near(supplier);
        }
    }

    private PacketDistributor() {
    }
}
