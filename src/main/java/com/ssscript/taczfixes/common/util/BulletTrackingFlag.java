package com.ssscript.taczfixes.common.util;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

public final class BulletTrackingFlag {
    private static EntityDataAccessor<Boolean> trackingDisabled;

    private BulletTrackingFlag() {
    }

    /** 勿在静态块调用: Forge 要求 defineId 的调用者必须是 Entity 子类, 由实体构造器调用(powered/客户端服务端均会经过)。 */
    public static void define() {
        if (trackingDisabled == null) {
            trackingDisabled = SynchedEntityData.defineId(EntityKineticBullet.class, EntityDataSerializers.BOOLEAN);
        }
    }

    public static EntityDataAccessor<Boolean> TRACKING_DISABLED() {
        return trackingDisabled;
    }
}
