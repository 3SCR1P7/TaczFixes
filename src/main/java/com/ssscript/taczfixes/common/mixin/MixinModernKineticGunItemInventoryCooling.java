package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.item.ModernKineticGunItem;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {ModernKineticGunItem.class}, remap = false)
public abstract class MixinModernKineticGunItemInventoryCooling {

    @Inject(method = {"inventoryTick"}, at = {@At("HEAD")}, cancellable = true, require = 0, remap = false)
    private void dualWield$skipOffhandInventoryCooling(ItemStack stack, Level level, Entity entity, int slot, boolean selected, CallbackInfo callback) {
        dualWield$cancelIfOffhandDualWield(stack, level, entity, callback);
    }

    @Inject(method = {"m_6883_"}, at = {@At("HEAD")}, cancellable = true, require = 0, remap = false)
    private void dualWield$skipOffhandInventoryCoolingSrg(ItemStack stack, Level level, Entity entity, int slot, boolean selected, CallbackInfo callback) {
        dualWield$cancelIfOffhandDualWield(stack, level, entity, callback);
    }

    private static void dualWield$cancelIfOffhandDualWield(ItemStack stack, Level level, Entity entity, CallbackInfo callback) {
        if (level == null || level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        if (stack != player.getOffhandItem() || !DualWieldEligibility.isDualWielding(player)) {
            return;
        }
        callback.cancel();
    }
}
