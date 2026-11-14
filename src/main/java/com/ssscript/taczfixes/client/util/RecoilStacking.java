package com.ssscript.taczfixes.client.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.jetbrains.annotations.Nullable;

/**
 * 后坐力叠加: 每一发开火的后坐力曲线作为独立图层。
 * <p>向上的部分逐发叠加(旧图层的抬升不会被下一发打断); 向下的回正部分会被下一发开火打断,
 * 因此停火后只有最后一发的向下段生效, 相机会回落约最后一发的抬升量, 之前叠加的抬升保留。
 */
public final class RecoilStacking {
    private static final int MAX_LAYERS = 64;
    private static final List<Layer> LAYERS = new ArrayList<>();

    private RecoilStacking() {
    }

    public static void reset() {
        LAYERS.clear();
    }

    public static void push(@Nullable PolynomialSplineFunction pitch,
                            @Nullable PolynomialSplineFunction yaw,
                            long startTime) {
        if (pitch == null && yaw == null) {
            return;
        }
        // 新的一发开火: 旧图层的向下段从这里开始被打断
        for (Layer layer : LAYERS) {
            layer.interrupted = true;
        }
        if (LAYERS.size() >= MAX_LAYERS) {
            LAYERS.remove(0);
        }
        LAYERS.add(new Layer(pitch, yaw, startTime));
    }

    /** 计算所有图层本帧的增量并应用到玩家视角。 */
    public static void apply(@Nullable LocalPlayer player) {
        if (player == null) {
            LAYERS.clear();
            return;
        }
        // TaCZ 的 shootTimeStamp 来自 GunTime(单调时钟), 必须用同一时间源取当前时间
        long now = com.tacz.guns.util.time.GunTime.nowMillis();
        double pitchDelta = 0.0;
        double yawDelta = 0.0;
        Iterator<Layer> iterator = LAYERS.iterator();
        while (iterator.hasNext()) {
            Layer layer = iterator.next();
            long elapsed = now - layer.startTime;
            if (elapsed < 0L) {
                continue;
            }
            pitchDelta += layer.pitch.advance(elapsed, layer.interrupted);
            yawDelta += layer.yaw.advance(elapsed, layer.interrupted);
            if (!layer.pitch.active && !layer.yaw.active) {
                iterator.remove();
            }
        }
        if (pitchDelta != 0.0) {
            player.setXRot(player.getXRot() - (float) pitchDelta);
        }
        if (yawDelta != 0.0) {
            player.setYRot(player.getYRot() - (float) yawDelta);
        }
    }

    private static final class Layer {
        private final long startTime;
        private final Axis pitch;
        private final Axis yaw;
        private boolean interrupted;

        private Layer(@Nullable PolynomialSplineFunction pitch, @Nullable PolynomialSplineFunction yaw,
                      long startTime) {
            this.startTime = startTime;
            this.pitch = new Axis(pitch);
            this.yaw = new Axis(yaw);
        }
    }

    /** 单条曲线的时间轴: 逐帧返回(当前值 − 上一帧值); 被后续开火打断时在曲线峰值之后停止贡献。 */
    private static final class Axis {
        private final @Nullable PolynomialSplineFunction spline;
        private final double endTime;
        private double last;
        private double peak;
        private boolean active;

        private Axis(@Nullable PolynomialSplineFunction spline) {
            this.spline = spline;
            this.active = spline != null;
            double[] knots = spline == null ? null : spline.getKnots();
            this.endTime = knots == null ? 0.0 : knots[knots.length - 1];
        }

        private double advance(long elapsed, boolean interrupted) {
            if (!active) {
                return 0.0;
            }
            double time = elapsed;
            boolean finalStep = false;
            if (time > endTime) {
                time = endTime;
                finalStep = true;
            }
            double value = spline.value(time);
            double delta = value - last;
            last = value;
            if (value > peak) {
                peak = value;
            }
            // 已经越过峰值、正在向下回正: 若存在更新的一发开火, 该向下段被打断
            boolean falling = value < peak;
            if (interrupted && falling) {
                active = false;
                return 0.0;
            }
            if (finalStep) {
                active = false;
            }
            return delta;
        }
    }
}
