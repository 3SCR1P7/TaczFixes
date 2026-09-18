package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.util.ChargeStorage;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** durability_bar: 用耐久栏显示剩余电量。 */
@Mixin(ItemStack.class)
public abstract class MixinItemStackChargeBar {

    @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
    private void taczfixes$chargeBarVisible(CallbackInfoReturnable<Boolean> callback) {
        if (taczfixes$usesChargeBar()) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
    private void taczfixes$chargeBarWidth(CallbackInfoReturnable<Integer> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!taczfixes$usesChargeBar()) {
            return;
        }
        int max = ChargeStorage.getMax(stack);
        int width = max <= 0 ? 0 : Math.round(13.0f * ChargeStorage.get(stack) / max);
        callback.setReturnValue(Math.max(0, Math.min(13, width)));
    }

    @Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
    private void taczfixes$chargeBarColor(CallbackInfoReturnable<Integer> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!taczfixes$usesChargeBar()) {
            return;
        }
        int max = ChargeStorage.getMax(stack);
        if (max <= 0) {
            callback.setReturnValue(0xFFFFFF);
            return;
        }
        float ratio = ChargeStorage.get(stack) / (float) max;
        callback.setReturnValue(Mth.hsvToRgb(ratio / 3.0f, 1.0f, 1.0f));
    }

    private boolean taczfixes$usesChargeBar() {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.isEmpty()) {
            return false;
        }
        GunTaczFixesData.ChargeConfig cfg = ChargeStorage.config(stack);
        return cfg != null && Boolean.TRUE.equals(cfg.durability_bar) && ChargeStorage.getMax(stack) > 0;
    }
}
