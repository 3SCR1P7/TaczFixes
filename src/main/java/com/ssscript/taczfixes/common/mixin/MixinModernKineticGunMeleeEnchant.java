package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.Config;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "com.tacz.guns.item.ModernKineticGunItem", remap = false)
public class MixinModernKineticGunMeleeEnchant {
    @ModifyArg(method = "lambda$melee$16", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/item/ModernKineticGunItem;doMelee(Lnet/minecraft/world/entity/LivingEntity;FFFFFLjava/util/List;)V"), index = 1)
    private float taczfixes$applyMeleeEnchantsGunDistance(LivingEntity user, float gunDistance, float meleeDistance, float rangeAngle, float knockback, float damage, List<?> effects) {
        ItemStack gun = GunEnchantmentHelper.getGunStack(user);
        if (gun.isEmpty()) {
            return gunDistance;
        }
        int sweep = GunEnchantmentHelper.getLevel(gun, Enchantments.SWEEPING_EDGE);
        if (sweep > 0) {
            double mult = Config.ENCH_SWEEPING_DISTANCE_MULT.get() * sweep;
            return (float) (gunDistance + gunDistance * mult);
        }
        return gunDistance;
    }

    @ModifyArg(method = "lambda$melee$16", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/item/ModernKineticGunItem;doMelee(Lnet/minecraft/world/entity/LivingEntity;FFFFFLjava/util/List;)V"), index = 2)
    private float taczfixes$applyMeleeEnchantsMeleeDistance(LivingEntity user, float gunDistance, float meleeDistance, float rangeAngle, float knockback, float damage, List<?> effects) {
        ItemStack gun = GunEnchantmentHelper.getGunStack(user);
        if (gun.isEmpty()) {
            return meleeDistance;
        }
        int sweep = GunEnchantmentHelper.getLevel(gun, Enchantments.SWEEPING_EDGE);
        if (sweep > 0) {
            double mult = Config.ENCH_SWEEPING_DISTANCE_MULT.get() * sweep;
            return (float) (meleeDistance + meleeDistance * mult);
        }
        return meleeDistance;
    }

    @ModifyArg(method = "lambda$melee$16", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/item/ModernKineticGunItem;doMelee(Lnet/minecraft/world/entity/LivingEntity;FFFFFLjava/util/List;)V"), index = 4)
    private float taczfixes$applyMeleeEnchantsKnockback(LivingEntity user, float gunDistance, float meleeDistance, float rangeAngle, float knockback, float damage, List<?> effects) {
        ItemStack gun = GunEnchantmentHelper.getGunStack(user);
        if (gun.isEmpty()) {
            return knockback;
        }
        int lvl = GunEnchantmentHelper.getLevel(gun, Enchantments.KNOCKBACK);
        if (lvl > 0) {
            double mult = Config.ENCH_KNOCKBACK_MELEE_MULT.get() * lvl;
            double flat = Config.ENCH_KNOCKBACK_MELEE_FLAT.get() * lvl;
            return (float) (knockback * (1.0 + mult) + flat);
        }
        return knockback;
    }

    @ModifyArg(method = "lambda$melee$16", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/item/ModernKineticGunItem;doMelee(Lnet/minecraft/world/entity/LivingEntity;FFFFFLjava/util/List;)V"), index = 5)
    private float taczfixes$applyMeleeEnchantsDamage(LivingEntity user, float gunDistance, float meleeDistance, float rangeAngle, float knockback, float damage, List<?> effects) {
        ItemStack gun = GunEnchantmentHelper.getGunStack(user);
        if (gun.isEmpty()) {
            return damage;
        }
        int sharp = GunEnchantmentHelper.getLevel(gun, Enchantments.SHARPNESS);
        if (sharp > 0) {
            return damage + (float) (sharp * Config.ENCH_SHARPNESS_MELEE_DAMAGE.get());
        }
        return damage;
    }

    @Inject(method = "doPerLivingHurt", at = @At("RETURN"), remap = false)
    private static void taczfixes$applyFireAspect(LivingEntity user, LivingEntity target,
                                                  float knockback, float damage, List<?> effects, CallbackInfo ci) {
        if (user == null || target == null || target.isDeadOrDying()) {
            return;
        }
        ItemStack gun = user.getMainHandItem();
        if (gun.isEmpty()) {
            return;
        }
        int fireAspect = GunEnchantmentHelper.getLevel(gun, Enchantments.FIRE_ASPECT);
        if (fireAspect <= 0) {
            return;
        }
        int ticks = fireAspect * Config.ENCH_FIRE_ASPECT_MELEE_TICKS.get();
        if (ticks <= 0) {
            return;
        }
        target.setSecondsOnFire(ticks / 20);
    }
}
