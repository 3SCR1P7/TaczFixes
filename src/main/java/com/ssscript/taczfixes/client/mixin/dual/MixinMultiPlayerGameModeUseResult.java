package com.ssscript.taczfixes.client.mixin.dual;

import com.ssscript.taczfixes.client.client.DualWieldClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameModeUseResult {
    @Inject(method = "useItem", at = @At("RETURN"))
    private void taczfixes$trackUseItem(Player player, InteractionHand hand,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        DualWieldClient.markVanillaUse(cir.getReturnValue());
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void taczfixes$trackUseItemOn(net.minecraft.client.player.LocalPlayer player, InteractionHand hand,
                                          BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        DualWieldClient.markVanillaUse(cir.getReturnValue());
    }

    @Inject(method = "interact", at = @At("RETURN"))
    private void taczfixes$trackInteract(Player player, Entity entity, InteractionHand hand,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        DualWieldClient.markVanillaUse(cir.getReturnValue());
    }

    @Inject(method = "interactAt", at = @At("RETURN"))
    private void taczfixes$trackInteractAt(Player player, Entity entity, EntityHitResult hit,
                                          InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        DualWieldClient.markVanillaUse(cir.getReturnValue());
    }
}
