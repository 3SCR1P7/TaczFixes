package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.util.StaminaHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原版跳跃: 耐力不足时无法跳跃, 跳跃成功后消耗耐力。 */
@Mixin(LivingEntity.class)
public class MixinLivingEntityJump {

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void taczfixes$blockJumpWhenExhausted(CallbackInfo ci) {
        if (!Config.STAMINA_ENABLED.get()) return;
        Object self = this;
        if (!(self instanceof Player player)) return;
        float cost = Config.STAMINA_JUMP_COST.get().floatValue();
        if (StaminaHelper.isInsufficient(player, cost)) {
            ci.cancel();
        }
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void taczfixes$consumeJumpStamina(CallbackInfo ci) {
        if (!Config.STAMINA_ENABLED.get()) return;
        Object self = this;
        if (!(self instanceof ServerPlayer player)) return;
        float cost = Config.STAMINA_JUMP_COST.get().floatValue();
        float weightFactor = 1f + StaminaHelper.gunWeight(player)
                * Config.STAMINA_WEIGHT_CONSUMPTION_PER_KG.get().floatValue();
        StaminaHelper.consumeWithGunMultiplier(player, cost * weightFactor);
    }
}
