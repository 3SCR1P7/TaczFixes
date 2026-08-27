package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.AmmoReplaceHelper;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GunHudOverlay.class, remap = false, priority = 2000)
public abstract class MixinGunHudOverlay {

    @Redirect(method = "handleInventoryAmmo",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IAmmo;isAmmoOfGun(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean taczfixes$isAmmoOfGun(IAmmo ammo, ItemStack gunStack, ItemStack ammoStack) {
        return AmmoReplaceHelper.checkAmmoOfGun(gunStack, ammoStack);
    }

    @Redirect(method = "handleInventoryAmmo",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IAmmoBox;isAmmoBoxOfGun(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean taczfixes$isAmmoBoxOfGun(IAmmoBox ammoBox, ItemStack gunStack, ItemStack boxStack) {
        return AmmoReplaceHelper.checkAmmoBoxOfGun(gunStack, boxStack);
    }
}
