package com.example.taczfixes.mixin;

import com.example.taczfixes.util.TaczExplosionContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.util.block.ExplodeUtil", remap = false)
public class MixinExplodeUtil {
    @Inject(method = "createExplosion(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;FF)V", at = @At("HEAD"), remap = false)
    private static void taczfixes$enterContext(Entity entity, Vec3 pos, float radius, float damage, CallbackInfo ci) {
        TaczExplosionContext.enter();
    }

    @Inject(method = "createExplosion(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;FF)V", at = @At("RETURN"), remap = false)
    private static void taczfixes$exitContext(Entity entity, Vec3 pos, float radius, float damage, CallbackInfo ci) {
        TaczExplosionContext.exit();
    }
}
