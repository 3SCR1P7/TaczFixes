package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.item.IGun;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 由枪械触发的法术不播放施法(玩家)动画。 */
@Mixin(targets = "io.redspace.ironsspellbooks.render.animation.AnimationHelper", remap = false)
public class MixinIronsCastAnimation {

    @Inject(method = "animatePlayerStart", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$skipGunCastAnimation(Player player, net.minecraft.resources.ResourceLocation animation,
                                                       CallbackInfo ci) {
        if (!Config.GUN_SPELL_ENABLED.get()) {
            return;
        }
        if (player == null || player != Minecraft.getInstance().player) {
            return;
        }
        if (taczfixes$isImbuedGun(player.getMainHandItem()) || taczfixes$isImbuedGun(player.getOffhandItem())) {
            ci.cancel();
        }
    }

    private static boolean taczfixes$isImbuedGun(ItemStack stack) {
        return stack != null && !stack.isEmpty() && IGun.getIGunOrNull(stack) != null
                && ISpellContainer.isSpellContainer(stack);
    }
}
