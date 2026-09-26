package com.ssscript.taczfixes.client.mixin;

import com.alrex.parcool.common.action.impl.Crawl;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.ssscript.taczfixes.common.util.ParCoolHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 持可爬行枪械时, ParCool 爬行不再设置自身的爬行动画(交由 TaCZ 第三人称动画接管)。
 */
@Mixin(value = Crawl.class, remap = false)
public class MixinParCoolCrawlAnimation {
    @Inject(method = "onWorkingTickInClient", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$skipParCoolCrawlAnimator(Player player, Parkourability parkourability, IStamina stamina, CallbackInfo ci) {
        if (!ParCoolHelper.isCrawling(player)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun != null && gun.isCanCrawl(stack)) {
            ci.cancel();
        }
    }
}
