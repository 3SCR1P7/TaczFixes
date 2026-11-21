package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.GunEnchantmentUtil;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 蓄势待发/封焰时刻只能附在对应功能(蓄力/过热)的枪上: 铁砧合成结果不合法时清空。 */
@Mixin(AnvilMenu.class)
public class MixinAnvilMenuRestrictedEnchants {
    @Inject(method = "createResult", at = @At("TAIL"))
    private void taczfixes$rejectRestrictedEnchants(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack result = menu.getSlot(2).getItem();
        if (result.isEmpty()) {
            return;
        }
        boolean invalid = false;
        if (GunEnchantmentUtil.level(result, TaczFixesMod.POISED_ENCHANTMENT) > 0
                && !GunEnchantmentUtil.hasChargeFeature(result)) {
            invalid = true;
        }
        if (GunEnchantmentUtil.level(result, TaczFixesMod.SEAL_FLARE_ENCHANTMENT) > 0
                && !GunEnchantmentUtil.hasHeatFeature(result)) {
            invalid = true;
        }
        if (invalid) {
            menu.getSlot(2).set(ItemStack.EMPTY);
        }
    }
}
