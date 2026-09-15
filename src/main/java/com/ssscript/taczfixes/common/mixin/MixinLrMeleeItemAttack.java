package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.LrMeleeStaminaManager;
import com.ssscript.taczfixes.common.handler.AimingStaminaHandler;
import com.ssscript.taczfixes.common.config.Config;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** lrtactical 近战武器攻击消耗上肢耐力(轻击/重击, 支持 index 内 taczfixes.stamina_consume 覆盖)。 */
@Mixin(targets = "me.xjqsh.lrtactical.item.MeleeItem", remap = false)
public class MixinLrMeleeItemAttack {

    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lme/xjqsh/lrtactical/api/melee/MeleeAction;Ljava/util/List;I)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$consumeAimingStamina(Player player, ItemStack stack, MeleeAction action,
                                                List<Entity> targets, int combo, CallbackInfo ci) {
        boolean light = action != MeleeAction.RIGHT;
        float cost = light
                ? Config.AIMING_STAMINA_LR_MELEE_LIGHT_COST.get().floatValue()
                : Config.AIMING_STAMINA_LR_MELEE_HEAVY_COST.get().floatValue();
        IMeleeWeapon weapon = IMeleeWeapon.of(stack);
        if (weapon != null) {
            ResourceLocation id = weapon.getId(stack);
            float[] override = LrMeleeStaminaManager.get(id);
            if (override != null) {
                float value = light ? override[0] : override[1];
                if (!Float.isNaN(value)) {
                    cost = value;
                }
            }
        }
        if (cost <= 0f) return;
        if (player instanceof ServerPlayer serverPlayer) {
            if (AimingStaminaHandler.isInsufficient(serverPlayer, cost)) {
                ci.cancel();
                return;
            }
            AimingStaminaHandler.consume(serverPlayer, cost);
        } else if (player.level().isClientSide
                && com.ssscript.taczfixes.client.util.AimingStaminaClientState.isInsufficient(cost)) {
            // 客户端预测: 耐力不足时不播放攻击动作
            ci.cancel();
        }
    }
}
