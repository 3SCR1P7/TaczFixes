package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.handler.AimingStaminaHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 枪械近战消耗上肢耐力。 */
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityMelee", remap = false)
public class MixinLivingEntityMelee {

    @Shadow(remap = false)
    private LivingEntity shooter;

    @Inject(method = "melee", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$consumeAimingStamina(CallbackInfo ci) {
        if (shooter == null || shooter.level().isClientSide) return;
        if (!(shooter instanceof ServerPlayer player)) return;
        ItemStack gun = player.getMainHandItem();
        GunTaczFixesData.AimingStaminaConfig cfg = AttachmentTaczFixesManager.resolveAimingStamina(gun);
        float cost = cfg.melee_cost.floatValue();
        if (AimingStaminaHandler.isInsufficient(player, cost)) {
            ci.cancel();
            return;
        }
        AimingStaminaHandler.consume(player, cost);
    }
}
