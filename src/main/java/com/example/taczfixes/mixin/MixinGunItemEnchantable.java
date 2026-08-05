package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.example.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让枪械可以附魔（附魔台/铁砧机制与 MC 原版工具一致）。
 * isEnchantable / getEnchantmentValue 定义在 Item 上，AbstractGunItem 未覆写，
 * 因此在 Item 层面以 IGun 类型判断进行注入。
 */
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
