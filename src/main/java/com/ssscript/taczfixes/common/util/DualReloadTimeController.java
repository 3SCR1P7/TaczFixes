package com.ssscript.taczfixes.common.util;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class DualReloadTimeController {
    private static final Map<ShooterDataHolder, ReloadSession> SESSIONS = Collections.synchronizedMap(new WeakHashMap());

    private DualReloadTimeController() {
    }

    public static void begin(ShooterDataHolder dataHolder, ItemStack stack, LivingEntity shooter) {
        if (dataHolder == null) {
            return;
        }
        synchronized (SESSIONS) {
            SESSIONS.remove(dataHolder);
            if (stack == null || stack.isEmpty() || shooter == null || shooter.level().isClientSide() || dataHolder.reloadTimestamp < 0 || !dataHolder.reloadStateType.isReloading() || !DualWieldEligibility.isDualWielding(shooter)) {
                return;
            }
            SESSIONS.put(dataHolder, new ReloadSession(stack, DualWieldStackId.getOrCreate(stack), DualWieldBalance.getReloadTimeScale(stack)));
        }
    }

    public static void end(ShooterDataHolder dataHolder, ItemStack stack) {
        if (dataHolder == null) {
            return;
        }
        synchronized (SESSIONS) {
            ReloadSession session = SESSIONS.get(dataHolder);
            if (session != null && (stack == null || session.matches(stack))) {
                SESSIONS.remove(dataHolder);
            }
        }
    }

    public static long toVirtualDuration(ShooterDataHolder dataHolder, ItemStack stack, long realDuration) {
        double timeScale = getTimeScale(dataHolder, stack);
        if (timeScale >= 1.0d) {
            return realDuration;
        }
        return multiplySaturated(realDuration, timeScale);
    }

    public static long toRealAdjustment(ShooterDataHolder dataHolder, ItemStack stack, long virtualAdjustment) {
        double timeScale = getTimeScale(dataHolder, stack);
        if (timeScale >= 1.0d) {
            return virtualAdjustment;
        }
        return divideSaturated(virtualAdjustment, timeScale);
    }

    public static long toRealCountDown(ShooterDataHolder dataHolder, ItemStack stack, long virtualCountDown) {
        double timeScale = getTimeScale(dataHolder, stack);
        if (virtualCountDown < 0 || timeScale >= 1.0d) {
            return virtualCountDown;
        }
        return divideSaturated(virtualCountDown, timeScale);
    }

    public static boolean isActive(ShooterDataHolder dataHolder, ItemStack stack) {
        return getTimeScale(dataHolder, stack) < 1.0d;
    }

    private static double getTimeScale(ShooterDataHolder dataHolder, ItemStack stack) {
        if (dataHolder == null || stack == null || stack.isEmpty()) {
            return 1.0d;
        }
        synchronized (SESSIONS) {
            ReloadSession session = SESSIONS.get(dataHolder);
            if (session == null) {
                return 1.0d;
            }
            if (dataHolder.reloadTimestamp < 0 || !dataHolder.reloadStateType.isReloading()) {
                SESSIONS.remove(dataHolder);
                return 1.0d;
            }
            return session.matches(stack) ? session.timeScale : 1.0d;
        }
    }

    private static long multiplySaturated(long value, double multiplier) {
        double result = value * multiplier;
        if (result >= 9.223372036854776E18d) {
            return Long.MAX_VALUE;
        }
        if (result <= -9.223372036854776E18d) {
            return Long.MIN_VALUE;
        }
        return (long) result;
    }

    private static long divideSaturated(long value, double divisor) {
        if (divisor <= 0.0d) {
            return value;
        }
        double result = value / divisor;
        if (result >= 9.223372036854776E18d) {
            return Long.MAX_VALUE;
        }
        if (result <= -9.223372036854776E18d) {
            return Long.MIN_VALUE;
        }
        return (long) result;
    }

    private static final class ReloadSession {
        private final WeakReference<ItemStack> stackReference;
        private final UUID stackId;
        private final double timeScale;

        private ReloadSession(ItemStack stack, UUID stackId, double timeScale) {
            double dMin;
            this.stackReference = new WeakReference<>(stack);
            this.stackId = stackId;
            if (Double.isFinite(timeScale) && timeScale > 0.0d) {
                dMin = Math.min(timeScale, 1.0d);
            } else {
                dMin = 1.0d;
            }
            this.timeScale = dMin;
        }

        private boolean matches(ItemStack stack) {
            if (this.stackId != null) {
                return this.stackId.equals(DualWieldStackId.get(stack));
            }
            return this.stackReference.get() == stack;
        }
    }
}
