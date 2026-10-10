package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.util.ChargeStorage;
import com.ssscript.taczfixes.common.util.DurabilityStorage;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** durability_bar: 用耐久栏显示枪械耐久或剩余电量。 */
@Mixin(ItemStack.class)
public abstract class MixinItemStackChargeBar {

    @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
    private void taczfixes$chargeBarVisible(CallbackInfoReturnable<Boolean> callback) {
        if (taczfixes$barRatio() >= 0.0f) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
    private void taczfixes$chargeBarWidth(CallbackInfoReturnable<Integer> callback) {
        float ratio = taczfixes$barRatio();
        if (ratio < 0.0f) {
            return;
        }
        callback.setReturnValue(Math.max(0, Math.min(13, Math.round(13.0f * ratio))));
    }

    @Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
    private void taczfixes$chargeBarColor(CallbackInfoReturnable<Integer> callback) {
        float ratio = taczfixes$barRatio();
        if (ratio < 0.0f) {
            return;
        }
        callback.setReturnValue(Mth.hsvToRgb(ratio / 3.0f, 1.0f, 1.0f));
    }

    /** 剩余比例(0-1); 不显示耐久栏时返回 -1。耐久条优先于电量条。 */
    private float taczfixes$barRatio() {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.isEmpty()) {
            return -1.0f;
        }
        GunTaczFixesData.DurabilityConfig durability = DurabilityStorage.config(stack);
        if (durability != null && Boolean.TRUE.equals(durability.durability_bar)) {
            int max = DurabilityStorage.getMax(stack);
            if (max > 0) {
                int value = DurabilityStorage.get(stack);
                // 满耐久不显示耐久条
                if (value < max) {
                    return value / (float) max;
                }
            }
        }
        GunTaczFixesData.ChargeConfig charge = ChargeStorage.config(stack);
        if (charge != null && Boolean.TRUE.equals(charge.durability_bar)) {
            int max = ChargeStorage.getMax(stack);
            if (max > 0) {
                return ChargeStorage.get(stack) / (float) max;
            }
        }
        return -1.0f;
    }
}
