package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.ssscript.taczfixes.client.render.DualRecoilMultiplier;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {DualRecoilMultiplier.class}, remap = false)
public abstract class MixinDualRecoilMultiplier {
    @Inject(method = {"resolveMultiplier"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private static void dualWield$resolveRecoilMultiplier(ItemStack stack, AttachmentCacheProperty cache, CallbackInfoReturnable<Double> callback) {
        Double multiplier = DualWieldOverrides.recoilMultiplier(stack);
        if (multiplier != null && Double.isFinite(multiplier.doubleValue()) && multiplier.doubleValue() >= 0.0d) {
            callback.setReturnValue(multiplier);
            return;
        }
        callback.setReturnValue(DualWieldEligibility.getClientRecoilMultiplier());
    }
}
