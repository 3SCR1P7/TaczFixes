package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 解除枪械附魔的互斥限制：原版对互相冲突的附魔（如锋利/亡灵杀手/节肢杀手等）
 * 通过 Enchantment.isCompatibleWith(Enchantment) 判定互斥，该方法没有 ItemStack 参数，
 * 因此借助 GunEnchantmentHelper 中由附魔台/铁砧流程设置的上下文标记，当目标物品为枪械时
 * 直接判定兼容，其余物品保持原版逻辑。
 */
@Mixin(Enchantment.class)
public class MixinEnchantmentGunCompatible {
    @Inject(method = "isCompatibleWith", at = @At("HEAD"), cancellable = true)
    private void taczfixes$allowGunConflicts(Enchantment other, CallbackInfoReturnable<Boolean> cir) {
        if (GunEnchantmentHelper.isGunEnchanting()) {
            cir.setReturnValue(true);
        }
    }
}
