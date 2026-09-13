package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.AimingStaminaClientState;
import com.ssscript.taczfixes.common.data.LrMeleeStaminaManager;
import com.ssscript.taczfixes.common.register.Config;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** lrtactical 近战攻击输入: 上肢耐力不足时不触发攻击动作。 */
@Mixin(targets = "me.xjqsh.lrtactical.client.input.AttackKeys", remap = false)
public class MixinLrAttackKeys {

    @Inject(method = "onNormalAttack", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$blockNormalAttack(InputEvent.MouseButton.Post event, CallbackInfo ci) {
        taczfixes$block(ci, true);
    }

    @Inject(method = "onSpAttack", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$blockSpecialAttack(InputEvent.MouseButton.Post event, CallbackInfo ci) {
        taczfixes$block(ci, false);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$blockTick(net.minecraftforge.event.TickEvent.ClientTickEvent event, CallbackInfo ci) {
        taczfixes$block(ci, true);
    }

    private static void taczfixes$block(CallbackInfo ci, boolean light) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IMeleeWeapon weapon)) return;
        float cost = light
                ? Config.AIMING_STAMINA_LR_MELEE_LIGHT_COST.get().floatValue()
                : Config.AIMING_STAMINA_LR_MELEE_HEAVY_COST.get().floatValue();
        ResourceLocation id = weapon.getId(stack);
        float[] override = LrMeleeStaminaManager.get(id);
        if (override != null) {
            float value = light ? override[0] : override[1];
            if (!Float.isNaN(value)) {
                cost = value;
            }
        }
        if (cost > 0f && AimingStaminaClientState.isInsufficient(cost)) {
            ci.cancel();
        }
    }
}
