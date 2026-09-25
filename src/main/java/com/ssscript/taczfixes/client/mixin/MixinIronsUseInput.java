package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.item.IGun;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 枪械触发的施法不应阻止攻击(左键), 否则全自动开火会被断断续续地取消。 */
@Mixin(targets = "io.redspace.ironsspellbooks.player.ClientInputEvents", remap = false)
public class MixinIronsUseInput {

    @Inject(method = "onUseInput", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$allowGunAttack(InputEvent.InteractionKeyMappingTriggered event, CallbackInfo ci) {
        if (!Config.GUN_SPELL_ENABLED.get() || !event.isAttack()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
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
