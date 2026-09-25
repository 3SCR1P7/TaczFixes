package com.ssscript.taczfixes.client.util;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** 记录 GameRenderer#getFov 两种调用(useFovSetting=true / false)的返回值, 供镜内/外 FOV 诊断。 */
@OnlyIn(Dist.CLIENT)
public final class ScopeFovDebug {

    private static volatile double fovWorld = -1.0;
    private static volatile double fovLens = -1.0;
    private static volatile double fovOther = -1.0;
    private static volatile boolean expectWorld;

    private ScopeFovDebug() {
    }

    /** 每帧开始重置。 */
    public static void resetFrame() {
        expectWorld = false;
    }

    /** 标记下一次 configured-FOV 调用为世界层(renderLevel 内)。 */
    public static void expectWorldCall() {
        expectWorld = true;
    }

    public static void capture(boolean useFovSetting, double fov) {
        if (useFovSetting) {
            if (expectWorld) {
                fovWorld = fov;
                expectWorld = false;
            } else {
                fovLens = fov;
            }
        } else {
            fovOther = fov;
        }
    }

    public static double getFovLens() {
        return fovLens;
    }

    public static double getFovWorld() {
        return fovWorld;
    }

    public static double getFovOther() {
        return fovOther;
    }
}
