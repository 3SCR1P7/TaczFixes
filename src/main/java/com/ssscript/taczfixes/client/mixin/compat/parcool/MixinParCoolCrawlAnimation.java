package com.ssscript.taczfixes.client.mixin.compat.parcool;

import com.alrex.parcool.common.action.impl.Crawl;
import com.alrex.parcool.common.attachment.common.Parkourability;
import com.ssscript.taczfixes.common.util.ParCoolHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a crawl-capable gun is held, let TaCZ drive the crawl pose instead of ParCool. */
@Mixin(value = Crawl.class, remap = false)
public class MixinParCoolCrawlAnimation {
    @Inject(method = "onWorkingTickInClient", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$skipParCoolCrawlAnimator(Player player, Parkourability parkourability,
                                                    CallbackInfo ci) {
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
