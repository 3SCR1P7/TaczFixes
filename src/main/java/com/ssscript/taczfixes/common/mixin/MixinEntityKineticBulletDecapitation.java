package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.DecapitationHelper;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityKineticBullet.class)
public class MixinEntityKineticBulletDecapitation {
    @Inject(method = "tick", at = @At("RETURN"), remap = true)
    private void taczfixes$onBulletTickEnd(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.isRemoved()) {
            DecapitationHelper.onBulletRemoved(self);
        }
    }
}
