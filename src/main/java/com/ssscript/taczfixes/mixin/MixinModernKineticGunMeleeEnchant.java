package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 近战附魔：
 * - 锋利：每级增加近战伤害
 * - 击退：按倍率 + 固定值增加近战击退
 * - 横扫之刃：按倍率增加近战距离
 * - 火焰附加：TACZ 的 doPerLivingHurt 直接调用 target.hurt()，绕过原版
 *   ServerPlayerGameMode.attack 的点燃逻辑，故在此按等级点燃目标。
 *
 * doMelee 签名：(LivingEntity user, float gunDistance, float meleeDistance, float rangeAngle, float knockback, float damage, List)
 * 生产 jar 中 doMelee 的调用点在 lambda$melee$16(ItemStack gunItem, LivingEntity user, CommonGunIndex) 内。
 * doPerLivingHurt 签名：(LivingEntity user, LivingEntity target, float knockback, float damage, List)
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunItem", remap = false)
public class MixinModernKineticGunMeleeEnchant {
    // 注意：不要使用 @ModifyArgs（Args 合成类在 lambda 中会触发
    // NoClassDefFoundError: org/spongepowered/asm/synthetic/args/Args$1），
    // 改为每个参数一个 @ModifyArg。@ModifyArg 多参形式要求回调参数
    // 与 doMelee 的完整参数列表一致（Arrays.equals 严格校验）。
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

    /**
     * doPerLivingHurt 在每次近战命中后调用；原版火焰附加的点燃依赖
     * ServerPlayerGameMode.attack，TACZ 直接 hurt() 不走该流程，故在此补上。
     */
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
