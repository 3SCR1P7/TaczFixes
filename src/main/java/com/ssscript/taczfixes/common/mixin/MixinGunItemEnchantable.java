package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class MixinGunItemEnchantable {
    @Inject(method = "isEnchantable", at = @At("HEAD"), cancellable = true)
    private void taczfixes$isGunEnchantable(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        if (stack.getItem() instanceof IGun) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getEnchantmentValue", at = @At("HEAD"), cancellable = true)
    private void taczfixes$getGunEnchantmentValue(CallbackInfoReturnable<Integer> cir) {
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        if ((Object) this instanceof IGun) {
            cir.setReturnValue(Config.GUN_ENCHANTMENT_VALUE.get());
        }
    }
}
