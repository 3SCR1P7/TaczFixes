package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.LimbDamageHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class MixinEntityKineticBullet {

    @Inject(method = "onHitEntity", at = @At("HEAD"), remap = false)
    private void storeHitLocation(TacHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        LimbDamageHelper.storeHitPosition(((EntityKineticBullet) (Object) this).getId(), result.getLocation());
    }
}
