package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractGunItem.class)
public class MixinGunItemInfinity {
    @Inject(method = "hasInventoryAmmo", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$infiniteHasInventoryAmmo(LivingEntity shooter, ItemStack gun, boolean needCheckAmmo,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (GunEnchantmentHelper.hasEnchant(gun, Enchantments.INFINITY_ARROWS)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "findAndExtractInventoryAmmo", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$infiniteInventoryAmmo(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount,
                                                 CallbackInfoReturnable<Integer> cir) {
        if (!GunEnchantmentHelper.hasEnchant(gunItem, Enchantments.INFINITY_ARROWS)) {
            return;
        }
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack check = itemHandler.getStackInSlot(i);
            if (check.isEmpty()) {
                continue;
            }
            AbstractGunItem self = (AbstractGunItem) (Object) this;
            if (check.getItem() instanceof com.tacz.guns.api.item.IAmmo iAmmo && iAmmo.isAmmoOfGun(gunItem, check)) {
                cir.setReturnValue(needAmmoCount);
                return;
            }
            if (check.getItem() instanceof com.tacz.guns.api.item.IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(gunItem, check)) {
                cir.setReturnValue(needAmmoCount);
                return;
            }
        }
        cir.setReturnValue(0);
    }

    @Inject(method = "findAndExtractDummyAmmo", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$infiniteDummyAmmo(ItemStack gunItem, int needAmmoCount, CallbackInfoReturnable<Integer> cir) {
        if (GunEnchantmentHelper.hasEnchant(gunItem, Enchantments.INFINITY_ARROWS)) {
            cir.setReturnValue(needAmmoCount);
        }
    }
}
