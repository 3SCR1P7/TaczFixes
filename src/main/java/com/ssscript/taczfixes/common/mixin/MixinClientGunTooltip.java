package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.AmmoReplaceHelper;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.client.tooltip.ClientGunTooltip;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientGunTooltip.class, remap = false)
public abstract class MixinClientGunTooltip {
    @Shadow
    @Final
    private ItemStack gun;

    @Shadow
    @Final
    private CommonGunIndex gunIndex;

    @Mutable
    @Shadow
    @Final
    private ItemStack ammo;

    @Shadow
    private Component ammoName;

    @Inject(method = "getText", at = @At("TAIL"), remap = false)
    private void taczfixes$ammoReplaceAfterGetText(CallbackInfo ci) {
        GunData gunData = gunIndex == null ? null : gunIndex.getGunData();
        ResourceLocation base = gunData == null ? null : gunData.getAmmoId();
        ResourceLocation replaced = AmmoReplaceHelper.resolveAmmoId(gun, base);
        if (replaced != null) {
            this.ammo = AmmoItemBuilder.create().setId(replaced).build();
            this.ammoName = this.ammo.getHoverName();
        }
    }
}