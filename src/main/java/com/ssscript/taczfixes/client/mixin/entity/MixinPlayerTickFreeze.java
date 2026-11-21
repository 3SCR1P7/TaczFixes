package com.ssscript.taczfixes.client.mixin.entity;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 常凪冻结: Player 自己重写了 tick, 需要单独拦截(其他玩家)。 */
@Mixin(Player.class)
public class MixinPlayerTickFreeze {
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void taczfixes$freezeClientTick(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide && FreezeClientState.isEntityFrozen(self)) {
            self.setOldPosAndRot();
            ci.cancel();
        }
    }
}
