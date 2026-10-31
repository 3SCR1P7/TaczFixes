package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelperGunResults {

    @Inject(method = "getAvailableEnchantmentResults", at = @At("HEAD"), cancellable = true)
    private static void taczfixes$getGunEnchantmentResults(int level, ItemStack stack,
                                                           Stream<Holder<Enchantment>> enchantments,
                                                           CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        if (!GunEnchantmentHelper.isEnabled() || !(stack.getItem() instanceof IGun)) {
            return;
        }
        List<EnchantmentInstance> results = new ArrayList<>();
        enchantments.filter(holder -> GunEnchantmentHelper.isEnchantAllowed(holder.unwrapKey().orElse(null)))
                .forEach(holder -> {
                    Enchantment enchantment = holder.value();
                    for (int enchantmentLevel = enchantment.getMaxLevel();
                         enchantmentLevel >= enchantment.getMinLevel(); enchantmentLevel--) {
                        if (level >= enchantment.getMinCost(enchantmentLevel)
                                && level <= enchantment.getMaxCost(enchantmentLevel)) {
                            results.add(new EnchantmentInstance(holder, enchantmentLevel));
                            break;
                        }
                    }
                });
        cir.setReturnValue(results);
    }
}
