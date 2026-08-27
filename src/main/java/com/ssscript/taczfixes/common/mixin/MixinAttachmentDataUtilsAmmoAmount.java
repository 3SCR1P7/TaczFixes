package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 弹匣容量修饰符: 在原版扩容弹匣(extended mag)容量计算之后,
 * 应用配件 data 的 ammo_amount 修饰符(addend/percent/multiplier/function)。
 */
@Mixin(AttachmentDataUtils.class)
public class MixinAttachmentDataUtilsAmmoAmount {

    @Inject(method = "getAmmoCountWithAttachment", at = @At("RETURN"), cancellable = true, remap = false)
    private static void taczfixes$applyAmmoAmountModifier(ItemStack gunItem, GunData gunData,
                                                          CallbackInfoReturnable<Integer> cir) {
        int base = cir.getReturnValue();
        if (base <= 0) {
            return;
        }
        double adjusted = AttachmentTaczFixesManager.applyAmmoAmount(gunItem, base);
        long value = (long) Math.floor(adjusted);
        if (value == base) {
            return;
        }
        cir.setReturnValue((int) Math.max(1L, value));
    }
}
