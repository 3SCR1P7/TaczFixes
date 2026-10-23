package com.ssscript.taczfixes.common.mixin;

import me.xjqsh.lrtactical.api.item.IConsumable;
import me.xjqsh.lrtactical.api.item.IThrowable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LRTactical consumables and throwables deliberately reject use from the
 * offhand. The dual-wield layout puts them there, so allow the normal use path
 * to start when the item is actually held in the offhand.
 */
@Mixin(targets = {
        "me.xjqsh.lrtactical.item.ConsumableItem",
        "me.xjqsh.lrtactical.item.ThrowableItem"
}, remap = false)
public abstract class MixinLrConsumableOffhandUse {

    @Inject(method = {"m_7203_", "use"}, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void taczfixes$allowOffhandUse(Level level, Player player, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callback) {
        if (hand != InteractionHand.OFF_HAND) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof IConsumable consumable) {
            if (consumable.getConsumableIndex(stack).isEmpty() || player.isUsingItem()) {
                return;
            }
        } else if (stack.getItem() instanceof IThrowable throwable) {
            if (throwable.getThrowableIndex(stack).isEmpty() || player.isUsingItem()) {
                return;
            }
        } else {
            return;
        }
        player.startUsingItem(hand);
        callback.setReturnValue(InteractionResultHolder.consume(stack));
    }
}
