package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.example.taczfixes.util.TaczExplosionContext;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntityKnockback {
    @Inject(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$blockKnockback(Vec3 motion, CallbackInfo ci) {
        if (Config.EXPLOSION_BULLET_ONLY.get() && TaczExplosionContext.isActive() && !((Entity)(Object)this instanceof EntityKineticBullet)) {
            ci.cancel();
        }
    }
}
