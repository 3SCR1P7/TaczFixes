package com.example.taczfixes.mixin;

import com.example.taczfixes.util.LimbDamageHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class EntityKineticBulletMixin {

    @Inject(method = "onHitEntity", at = @At("HEAD"), remap = false)
    private void storeHitLocation(TacHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        LimbDamageHelper.storeHitPosition(((EntityKineticBullet) (Object) this).getId(), result.getLocation());
    }
}
