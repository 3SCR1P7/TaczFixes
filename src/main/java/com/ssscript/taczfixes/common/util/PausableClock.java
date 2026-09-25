package com.ssscript.taczfixes.common.util;

/**
 * 可暂停时钟: 单人游戏暂停(含失去焦点暂停)期间不推进。
 * TACZ 的换弹进度、动画播放等使用墙钟计时, 暂停时仍会继续流逝, 这里统一替换为可暂停时间。
 * 专用服务器不会被暂停, 因此始终等同真实时间。
 * 所有读写都加锁并保证返回值单调不减, 避免多线程(AI/声音线程)读取到撕裂的中间状态。
 */
public final class PausableClock {

    private static long pausedMillis;
    private static long pausedNanos;
    private static long pauseStartMillis = -1L;
    private static long pauseStartNanos = -1L;
    private static long lastMillis;
    private static long lastNanos;

    private PausableClock() {
    }

    /** 由客户端每帧调用。 */
    public static synchronized void setPaused(boolean paused) {
        if (paused) {
            if (pauseStartMillis < 0L) {
                pauseStartMillis = System.currentTimeMillis();
                pauseStartNanos = System.nanoTime();
            }
            return;
        }
        if (pauseStartMillis >= 0L) {
            pausedMillis += System.currentTimeMillis() - pauseStartMillis;
            pausedNanos += System.nanoTime() - pauseStartNanos;
            pauseStartMillis = -1L;
            pauseStartNanos = -1L;
        }
    }

    /** 可暂停的毫秒时间(暂停期间保持不变, 永不倒退)。 */
    public static synchronized long millis() {
        long value = pauseStartMillis >= 0L
                ? pauseStartMillis - pausedMillis
                : System.currentTimeMillis() - pausedMillis;
        if (value < lastMillis) {
            value = lastMillis;
        }
        lastMillis = value;
        return value;
    }

    /** 可暂停的纳秒时间(暂停期间保持不变, 永不倒退)。 */
    public static synchronized long nanos() {
        long value = pauseStartNanos >= 0L
                ? pauseStartNanos - pausedNanos
                : System.nanoTime() - pausedNanos;
        if (value < lastNanos) {
            value = lastNanos;
        }
        lastNanos = value;
        return value;
    }
}
