package com.ssscript.taczfixes.client.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ForwardOffsetSmoothing {
    private static final long DURATION_NANOS = 200_000_000L;
    private static final ThreadLocal<State> MAIN = ThreadLocal.withInitial(State::new);
    private static final ThreadLocal<State> OFFHAND = ThreadLocal.withInitial(State::new);

    private ForwardOffsetSmoothing() {
    }

    public static double mainHand(double target) {
        return MAIN.get().sample(target, System.nanoTime());
    }

    public static double offhand(double target) {
        return OFFHAND.get().sample(target, System.nanoTime());
    }

    private static final class State {
        private double from;
        private double to;
        private long startNanos;
        private boolean active;

        private double sample(double target, long now) {
            if (!this.active) {
                this.active = true;
                this.from = 0.0d;
                this.to = target;
                this.startNanos = now;
                return 0.0d;
            }
            if (Double.compare(target, this.to) != 0) {
                this.from = this.value(now);
                this.to = target;
                this.startNanos = now;
            }
            return this.value(now);
        }

        private double value(long now) {
            long elapsed = now - this.startNanos;
            if (elapsed >= DURATION_NANOS) {
                return this.to;
            }
            double progress = Math.max(0.0d, (double) elapsed / (double) DURATION_NANOS);
            double eased = progress * progress * (3.0d - 2.0d * progress);
            return this.from + (this.to - this.from) * eased;
        }
    }
}
