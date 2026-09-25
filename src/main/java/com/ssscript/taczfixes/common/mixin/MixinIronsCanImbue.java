package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 允许枪械在奥术铁砧上被注入 Iron's Spellbooks 法术(枪械 data 的 imbuement.enable 优先)。 */
@Mixin(targets = "io.redspace.ironsspellbooks.api.util.Utils", remap = false)
public class MixinIronsCanImbue {

    @Inject(method = "canImbue", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$allowGunImbue(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (IGun.getIGunOrNull(stack) != null) {
            cir.setReturnValue(Boolean.TRUE.equals(TaczFixesDataManager.resolveImbuement(stack).enable));
        }
    }
}
