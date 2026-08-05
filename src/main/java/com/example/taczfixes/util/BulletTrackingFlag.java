package com.example.taczfixes.util;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

public final class BulletTrackingFlag {
    public static final EntityDataAccessor<Boolean> TRACKING_DISABLED =
            SynchedEntityData.defineId(EntityKineticBullet.class, EntityDataSerializers.BOOLEAN);

    private BulletTrackingFlag() {
    }
}
