package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.PeekHeadshotHelper;
import com.tacz.guns.entity.EntityKineticBullet.EntityResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656peek.compat.tacz.TaczPeekHitboxHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TaczPeekHitboxHelper.class)
public class MixinTaczPeekHeadshotLimit {
    @Inject(method = "getHitResult", at = @At("RETURN"), cancellable = true, remap = false)
    private static void taczfixes$limitPeekHeadshot(Projectile projectile, Entity entity, Vec3 startVec, Vec3 endVec, CallbackInfoReturnable<EntityResult> cir) {
        EntityResult result = cir.getReturnValue();
        if (result == null || !result.isHeadshot()) {
            return;
        }
        if (PeekHeadshotHelper.shouldDemoteToBodyShot(entity, result.getHitPos())) {
            cir.setReturnValue(new EntityResult(result.getEntity(), result.getHitPos(), false));
        }
    }
}