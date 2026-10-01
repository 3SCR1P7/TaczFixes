package com.ssscript.taczfixes.common.util;

import net.minecraft.network.syncher.EntityDataAccessor;

public final class BulletTrackingFlag {
    private static EntityDataAccessor<Boolean> trackingDisabled;

    private BulletTrackingFlag() {
    }

    /** 由 MixinBulletTrackingFlag 在实体类内部定义后写入(避免 Forge 的 defineId 外部调用警告)。 */
    public static void set(EntityDataAccessor<Boolean> accessor) {
        trackingDisabled = accessor;
    }

    public static EntityDataAccessor<Boolean> TRACKING_DISABLED() {
        return trackingDisabled;
    }
}
