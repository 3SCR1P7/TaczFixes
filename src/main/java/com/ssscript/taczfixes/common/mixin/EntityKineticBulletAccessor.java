package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityKineticBullet.class)
public interface EntityKineticBulletAccessor {
    @Accessor(value = "shotDamageMultiplier", remap = false)
    float taczfixes$getShotDamageMultiplier();

    @Accessor(value = "ammoId", remap = false)
    void taczfixes$setAmmoId(ResourceLocation ammoId);
}
