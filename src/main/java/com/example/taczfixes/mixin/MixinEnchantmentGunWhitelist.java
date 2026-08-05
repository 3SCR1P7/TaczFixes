package com.example.taczfixes.mixin;

import com.example.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 附魔白名单：附魔台与铁砧都会经过 Enchantment.canApplyAtEnchantingTable(ItemStack)，
 * 对枪械返回白名单判断结果，其余物品保持原版逻辑。
 */
@Mixin(Enchantment.class)
public class MixinEnchantmentGunWhitelist {
    @Inject(method = "canApplyAtEnchantingTable", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$gunWhitelist(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        if (stack.getItem() instanceof IGun) {
            cir.setReturnValue(GunEnchantmentHelper.isEnchantAllowed((Enchantment) (Object) this));
        }
    }
}
