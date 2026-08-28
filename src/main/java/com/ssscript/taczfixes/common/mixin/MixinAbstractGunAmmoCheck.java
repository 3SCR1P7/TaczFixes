package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.AmmoReplaceHelper;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.tacz.guns.api.item.gun.AbstractGunItem", remap = false)
public abstract class MixinAbstractGunAmmoCheck {
    @Redirect(method = "findAndExtractInventoryAmmo",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IAmmo;isAmmoOfGun(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean taczfixes$isAmmoOfGun(IAmmo ammo, ItemStack gunStack, ItemStack ammoStack) {
        return AmmoReplaceHelper.checkAmmoOfGun(gunStack, ammoStack);
    }

    @Redirect(method = "lambda$hasInventoryAmmo$6",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IAmmo;isAmmoOfGun(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean taczfixes$isAmmoOfGunHas(IAmmo ammo, ItemStack gunStack, ItemStack ammoStack) {
        return AmmoReplaceHelper.checkAmmoOfGun(gunStack, ammoStack);
    }

    @Redirect(method = "lambda$canReload$1",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IAmmo;isAmmoOfGun(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean taczfixes$isAmmoOfGunReload(IAmmo ammo, ItemStack gunStack, ItemStack ammoStack) {
        return AmmoReplaceHelper.checkAmmoOfGun(gunStack, ammoStack);
    }

    @Redirect(method = "findAndExtractInventoryAmmo",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IAmmoBox;isAmmoBoxOfGun(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean taczfixes$isAmmoBoxOfGun(IAmmoBox ammoBox, ItemStack gunStack, ItemStack boxStack) {
        return AmmoReplaceHelper.checkAmmoBoxOfGun(gunStack, boxStack);
    }

    @Redirect(method = "lambda$hasInventoryAmmo$6",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IAmmoBox;isAmmoBoxOfGun(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean taczfixes$isAmmoBoxOfGunHas(IAmmoBox ammoBox, ItemStack gunStack, ItemStack boxStack) {
        return AmmoReplaceHelper.checkAmmoBoxOfGun(gunStack, boxStack);
    }

    @Redirect(method = "lambda$canReload$1",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IAmmoBox;isAmmoBoxOfGun(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean taczfixes$isAmmoBoxOfGunReload(IAmmoBox ammoBox, ItemStack gunStack, ItemStack boxStack) {
        return AmmoReplaceHelper.checkAmmoBoxOfGun(gunStack, boxStack);
    }
}