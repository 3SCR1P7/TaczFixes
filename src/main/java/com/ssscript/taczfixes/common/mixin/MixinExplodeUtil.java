package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.TaczFixesMod;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.ssscript.taczfixes.common.util.TaczExplosionContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.util.ExplodeUtil")
public class MixinExplodeUtil {
    @Inject(method = "createExplosion(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;FFZZLnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), remap = false)
    private static void taczfixes$enterContext(Entity owner, Entity entity, float damage, float radius,
                                               boolean fire, boolean grief, Vec3 pos, CallbackInfo ci) {
        TaczExplosionContext.enter();
    }

    @Inject(method = "createExplosion(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;FFZZLnet/minecraft/world/phys/Vec3;)V", at = @At("RETURN"), remap = false)
    private static void taczfixes$exitContext(Entity owner, Entity entity, float damage, float radius,
                                              boolean fire, boolean grief, Vec3 pos, CallbackInfo ci) {
        TaczExplosionContext.exit();
    }

    @ModifyVariable(method = "createExplosion(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;FFZZLnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), index = 2, argsOnly = true, remap = false)
    private static float taczfixes$expertDoubleDamage(float damage, Entity owner, Entity bullet, float originalDamage,
                                                      float radius, boolean fire, boolean grief, Vec3 pos) {
        if (damage <= 0.0F) {
            return damage;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return damage;
        }
        if (!(owner instanceof LivingEntity shooter)) {
            return damage;
        }
        int level = GunEnchantmentHelper.getLevel(shooter.getMainHandItem(),
                TaczFixesMod.EXPLOSION_EXPERT_ENCHANTMENT.get());
        if (level <= 0) {
            return damage;
        }
        return damage * (1 << level);
    }
}