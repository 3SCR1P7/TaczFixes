package com.ssscript.taczfixes.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityKineticBullet.class)
public interface EntityKineticBulletAccessor {
    @Accessor(value = "shotDamageMultiplier", remap = false)
    float taczfixes$getShotDamageMultiplier();
}
