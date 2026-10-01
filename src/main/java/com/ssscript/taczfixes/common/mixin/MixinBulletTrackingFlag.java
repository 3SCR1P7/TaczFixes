package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.BulletTrackingFlag;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class MixinBulletTrackingFlag {

    /** defineId 必须由实体类自身调用(Forge 检查), 因此放在合并进 EntityKineticBullet 的方法里。 */
    @Unique
    private static EntityDataAccessor<Boolean> taczfixes$trackingDisabled;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void taczfixes$defineTrackingFlag(CallbackInfo ci) {
        if (taczfixes$trackingDisabled == null) {
            taczfixes$trackingDisabled = SynchedEntityData.defineId(EntityKineticBullet.class, EntityDataSerializers.BOOLEAN);
            BulletTrackingFlag.set(taczfixes$trackingDisabled);
        }
        ((EntityKineticBullet) (Object) this).getEntityData().define(taczfixes$trackingDisabled, false);
    }
}
