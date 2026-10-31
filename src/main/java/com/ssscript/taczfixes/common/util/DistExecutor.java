package com.ssscript.taczfixes.common.util;

import java.util.function.Supplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class DistExecutor {
    private DistExecutor() {
    }

    public static void unsafeRunWhenOn(Dist dist, Supplier<? extends Runnable> runnable) {
        if (FMLEnvironment.dist == dist) {
            runnable.get().run();
        }
    }

    public static <T> T unsafeRunForDist(Supplier<? extends Supplier<T>> client,
                                         Supplier<? extends Supplier<T>> server) {
        return (FMLEnvironment.dist == Dist.CLIENT ? client.get() : server.get()).get();
    }

    public static <T> T callWhenOn(Dist dist, Supplier<? extends Supplier<T>> supplier) {
        return FMLEnvironment.dist == dist ? supplier.get().get() : null;
    }
}
