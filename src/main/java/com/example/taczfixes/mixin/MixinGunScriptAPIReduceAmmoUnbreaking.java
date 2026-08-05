package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.example.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 耐久附魔：开火消耗弹药（reduceAmmoOnce）前按概率跳过，使本次开火不消耗子弹。
 */
@Mixin(ModernKineticGunScriptAPI.class)
public class MixinGunScriptAPIReduceAmmoUnbreaking {
    @Shadow(remap = false)
    private LivingEntity shooter;

    @Shadow(remap = false)
    private ItemStack itemStack;

    @Inject(method = "reduceAmmoOnce", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$unbreakingPreserveAmmo(CallbackInfoReturnable<Boolean> cir) {
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        if (shooter == null || itemStack == null || itemStack.isEmpty()) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(itemStack, Enchantments.UNBREAKING);
        if (level <= 0) {
            return;
        }
        double chance = Math.min(Config.ENCH_UNBREAKING_NO_CONSUME_CHANCE.get() * level, 1.0);
        if (shooter.getRandom().nextDouble() < chance) {
            cir.setReturnValue(true);
        }
    }
}
