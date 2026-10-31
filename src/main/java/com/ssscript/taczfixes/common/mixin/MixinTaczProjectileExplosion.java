package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.tacz.guns.util.block.ProjectileExplosion", remap = false)
public class MixinTaczProjectileExplosion {
    @Shadow(remap = false)
    private Entity exploder;

    @Shadow(remap = false)
    private boolean knockback;

    @Redirect(method = "explode", at = @At(value = "FIELD",
            target = "Lcom/tacz/guns/util/block/ProjectileExplosion;knockback:Z", remap = false), remap = false)
    private boolean taczfixes$redirectKnockback(com.tacz.guns.util.block.ProjectileExplosion instance) {
        if (Config.EXPLOSION_BULLET_ONLY.get()) {
            return instance instanceof com.tacz.guns.util.block.ProjectileExplosion
                    && this.exploder instanceof EntityKineticBullet;
        }
        return true;
    }
}
