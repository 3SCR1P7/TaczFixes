package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.handler.NewGunEnchantmentHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 被常凪冻结的玩家无法进行近战攻击。 */
@Mixin(Player.class)
public class MixinPlayerAttackFreeze {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void taczfixes$blockFrozenAttack(Entity target, CallbackInfo ci) {
        if (NewGunEnchantmentHandler.isFrozen((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
