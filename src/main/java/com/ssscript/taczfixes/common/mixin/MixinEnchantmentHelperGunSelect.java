package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Stream;

@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelperGunSelect {

    @Inject(method = "selectEnchantment", at = @At("HEAD"))
    private static void taczfixes$markGunSelect(RandomSource random, ItemStack stack, int level,
                                               Stream<Holder<Enchantment>> enchantments,
                                               CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        GunEnchantmentHelper.setGunEnchanting(stack.getItem() instanceof IGun);
    }

    @Inject(method = "selectEnchantment", at = @At("RETURN"))
    private static void taczfixes$clearGunSelect(RandomSource random, ItemStack stack, int level,
                                                 Stream<Holder<Enchantment>> enchantments,
                                                 CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        GunEnchantmentHelper.setGunEnchanting(false);
    }
}
