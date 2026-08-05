package com.example.taczfixes.mixin;

import com.example.taczfixes.SpreadState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "com.tacz.guns.item.ModernKineticGunItem", remap = false)
public class MixinModernKineticGunBulletSpread {
    @ModifyVariable(method = "doBulletSpread", at = @At("HEAD"), argsOnly = true, index = 7)
    private float taczfixes$modifyInaccuracy(float inaccuracy) {
        return SpreadState.modifyInaccuracy(inaccuracy);
    }
}
