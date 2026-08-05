package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.tacz.guns.util.block.ProjectileExplosion", remap = false)
public class MixinTaczProjectileExplosion {
    private static final ThreadLocal<Entity> taczfixes$currentEntity = new ThreadLocal<>();

    @ModifyVariable(method = "explode()V", at = @At(value = "STORE", ordinal = 0), index = 13, remap = true)
    private Entity taczfixes$captureEntity(Entity entity) {
        if (entity != null) {
            taczfixes$currentEntity.set(entity);
        }
        return entity;
    }

    @Redirect(method = "explode()V", at = @At(value = "FIELD", target = "Lcom/tacz/guns/util/block/ProjectileExplosion;knockback:Z", remap = false), remap = true)
    private boolean taczfixes$redirectKnockback(com.tacz.guns.util.block.ProjectileExplosion instance) {
        if (Config.EXPLOSION_BULLET_ONLY.get()) {
            Entity entity = taczfixes$currentEntity.get();
            return entity != null && entity.getType() == EntityKineticBullet.TYPE;
        }
        return true;
    }
}
