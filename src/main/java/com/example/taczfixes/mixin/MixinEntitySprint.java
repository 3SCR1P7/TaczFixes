package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntitySprint {
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void taczfixes$onSetSprinting(boolean sprinting, CallbackInfo ci) {
        if (!sprinting) return;
        if (!Config.ADS_INTERRUPT_SPRINT.get()) return;

        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide() && self instanceof LocalPlayer player) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == player && IGun.mainHandHoldGun(player)) {
                if (IClientPlayerGunOperator.fromLocalPlayer(player).isAim()) {
                    ci.cancel();
                }
            }
        }
    }
}
