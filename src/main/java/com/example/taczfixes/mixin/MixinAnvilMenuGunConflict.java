package com.example.taczfixes.mixin;

import com.example.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 铁砧合成时，若左侧待合成的物品为枪械，则标记“枪械附魔”上下文，
 * 使本次合成过程中 Enchantment.isCompatibleWith 忽略冲突，
 * 从而允许把与枪上已有附魔冲突的附魔书（如亡灵杀手）直接合成到枪上。
 */
@Mixin(AnvilMenu.class)
public class MixinAnvilMenuGunConflict {
    @Inject(method = "createResult", at = @At("HEAD"))
    private void taczfixes$markGunAnvil(CallbackInfo ci) {
        ItemStack target = ((AnvilMenu) (Object) this).getSlot(0).getItem();
        GunEnchantmentHelper.setGunEnchanting(!target.isEmpty() && target.getItem() instanceof IGun);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void taczfixes$clearGunAnvil(CallbackInfo ci) {
        GunEnchantmentHelper.setGunEnchanting(false);
    }
}
