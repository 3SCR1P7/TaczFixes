package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.example.taczfixes.util.GunEnchantmentHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * 近战附魔：
 * - 锋利：每级增加近战伤害
 * - 击退：按倍率 + 固定值增加近战击退
 * - 横扫之刃：按倍率增加近战距离
 * 火焰附加（4秒/级）由原版 doEnchantDamageEffects 机制原生生效。
 *
 * doMelee 签名：(LivingEntity user, float gunDistance, float meleeDistance, float rangeAngle, float knockback, float damage, List)
 * 生产 jar 中 doMelee 的调用点在 lambda$melee$16(ItemStack gunItem, LivingEntity user, CommonGunIndex) 内。
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunItem", remap = false)
public class MixinModernKineticGunMeleeEnchant {
    @ModifyArgs(method = "lambda$melee$16", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/item/ModernKineticGunItem;doMelee(Lnet/minecraft/world/entity/LivingEntity;FFFFFLjava/util/List;)V"))
    private void taczfixes$applyMeleeEnchants(Args args) {
        LivingEntity user = args.get(0);
        ItemStack gun = GunEnchantmentHelper.getGunStack(user);
        if (gun.isEmpty()) {
            return;
        }
        // 锋利
        int sharp = GunEnchantmentHelper.getLevel(gun, Enchantments.SHARPNESS);
        if (sharp > 0) {
            float damage = args.get(5);
            args.set(5, damage + (float) (sharp * Config.ENCH_SHARPNESS_MELEE_DAMAGE.get()));
        }
        // 击退
        int lvl = GunEnchantmentHelper.getLevel(gun, Enchantments.KNOCKBACK);
        if (lvl > 0) {
            float knockback = args.get(4);
            double mult = Config.ENCH_KNOCKBACK_MELEE_MULT.get() * lvl;
            double flat = Config.ENCH_KNOCKBACK_MELEE_FLAT.get() * lvl;
            args.set(4, (float) (knockback * (1.0 + mult) + flat));
        }
        // 横扫之刃：放大枪械距离与近战距离
        int sweep = GunEnchantmentHelper.getLevel(gun, Enchantments.SWEEPING_EDGE);
        if (sweep > 0) {
            double mult = Config.ENCH_SWEEPING_DISTANCE_MULT.get() * sweep;
            float gunDistance = args.get(1);
            float meleeDistance = args.get(2);
            args.set(1, (float) (gunDistance + gunDistance * mult));
            args.set(2, (float) (meleeDistance + meleeDistance * mult));
        }
    }
}
