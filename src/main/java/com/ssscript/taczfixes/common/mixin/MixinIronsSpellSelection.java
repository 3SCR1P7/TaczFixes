package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 枪械上的法术不加入法术列表: 使其不出现在法术轮盘, 也不能通过施法键发动。 */
@Mixin(targets = "io.redspace.ironsspellbooks.api.magic.SpellSelectionManager", remap = false)
public class MixinIronsSpellSelection {

    @Inject(method = "initItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$skipGunSpell(ItemStack stack, String slot, CallbackInfo ci) {
        if (!Config.GUN_SPELL_ENABLED.get()) {
            return;
        }
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (IGun.getIGunOrNull(stack) != null) {
            ci.cancel();
        }
    }
}
