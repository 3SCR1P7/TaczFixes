package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 附魔台选择附魔时，若目标物品为枪械则标记“枪械附魔”上下文，
 * 使本次选择过程中 Enchantment.isCompatibleWith 忽略冲突（允许同一把枪上出现互相冲突的附魔）。
 * 附魔台与命令附魔最终都会经过 EnchantmentHelper.selectEnchantment。
 */
@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelperGunSelect {
    @Inject(method = "selectEnchantment", at = @At("HEAD"))
    private static void taczfixes$markGunSelect(RandomSource random, ItemStack stack, int level, boolean allowTreasure, CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        GunEnchantmentHelper.setGunEnchanting(stack.getItem() instanceof IGun);
    }

    @Inject(method = "selectEnchantment", at = @At("TAIL"))
    private static void taczfixes$clearGunSelect(CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        GunEnchantmentHelper.setGunEnchanting(false);
    }
}
