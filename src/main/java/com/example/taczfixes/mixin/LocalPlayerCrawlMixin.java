package com.example.taczfixes.mixin;

import com.example.taczfixes.util.ParCoolHelper;
import com.tacz.guns.client.gameplay.LocalPlayerCrawl;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayerCrawl.class)
public class LocalPlayerCrawlMixin {
    @Shadow(remap = false)
    private LocalPlayer player;
    @Shadow(remap = false)
    private boolean isCrawling;

    @Unique
    private boolean taczfixes$wasParCoolCrawling;

    @Inject(method = "tickCrawl", at = @At("HEAD"), cancellable = true, remap = false)
    private void onTickCrawl(CallbackInfo ci) {
        if (ParCoolHelper.isCrawling(player)) {
            isCrawling = true;
            taczfixes$wasParCoolCrawling = true;
            ci.cancel();
        } else if (taczfixes$wasParCoolCrawling) {
            isCrawling = false;
            taczfixes$wasParCoolCrawling = false;
            ci.cancel();
        }
    }

    @Inject(method = "setCrawlPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSetCrawlPose(CallbackInfo ci) {
        if (ParCoolHelper.isCrawling(player)) {
            ci.cancel();
        }
    }
}
