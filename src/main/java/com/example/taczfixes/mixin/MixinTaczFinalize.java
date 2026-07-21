package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "com.tacz.guns.util.block.ProjectileExplosion", remap = false)
public class MixinTaczFinalize {
    @Inject(method = "finalizeExplosion(Ljava/util/List;)V", at = @At("HEAD"), remap = false)
    private void taczfixes$filterFinalize(List<Entity> entities, CallbackInfo ci) {
        if (Config.EXPLOSION_BULLET_ONLY.get()) {
            entities.removeIf(e -> !(e instanceof EntityKineticBullet));
        }
    }
}
