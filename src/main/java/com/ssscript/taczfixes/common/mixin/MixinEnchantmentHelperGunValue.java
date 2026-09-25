package com.ssscript.taczfixes.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 枪械 data 的 enchantment_ability 覆盖配置文件中的附魔能力值(附魔台消耗/可选附魔)。 */
@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelperGunValue {

    @WrapOperation(method = {"m_220287_", "m_220297_"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;m_6473_()I"), require = 0)
    private static int taczfixes$gunEnchantmentValue(Item item, Operation<Integer> original,
                                                     @Local(argsOnly = true) ItemStack stack) {
        if (stack != null && GunEnchantmentHelper.isEnabled() && stack.getItem() instanceof IGun) {
            Integer ability = TaczFixesDataManager.getEnchantmentAbility(stack);
            if (ability != null && ability > 0) {
                return ability;
            }
        }
        return original.call(item);
    }
}
