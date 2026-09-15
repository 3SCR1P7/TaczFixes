package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.api.item.IGun;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {DualWieldEligibility.class}, remap = false)
public abstract class MixinDualWieldEligibility {
    @Inject(method = {"isEligibleGun(Lnet/minecraft/world/item/ItemStack;Lcom/ssscript/taczfixes/common/util/DualWieldEligibility$Rules;)Z"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private static void dualWield$applyPerGunEnable(ItemStack stack, DualWieldEligibility.Rules rules, CallbackInfoReturnable<Boolean> callback) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return;
        }
        Boolean enabled = DualWieldOverrides.enabled(gun.getGunId(stack));
        if (enabled != null) {
            callback.setReturnValue(enabled);
        }
    }
}
