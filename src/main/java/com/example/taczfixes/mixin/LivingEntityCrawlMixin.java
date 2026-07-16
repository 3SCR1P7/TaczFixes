package com.example.taczfixes.mixin;

import com.example.taczfixes.util.ParCoolHelper;
import com.tacz.guns.entity.shooter.LivingEntityCrawl;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityCrawl.class)
public class LivingEntityCrawlMixin {
    @Shadow(remap = false)
    @Final
    private LivingEntity shooter;
    @Shadow(remap = false)
    @Final
    private ShooterDataHolder data;

    @Unique
    private boolean taczfixes$wasParCoolCrawling;

    @Inject(method = "tickCrawling", at = @At("HEAD"), cancellable = true, remap = false)
    private void onTickCrawling(CallbackInfo ci) {
        boolean nowParCoolCrawling = ParCoolHelper.isCrawling(shooter);
        if (nowParCoolCrawling) {
            data.isCrawling = true;
            taczfixes$wasParCoolCrawling = true;
            ci.cancel();
        } else if (taczfixes$wasParCoolCrawling) {
            data.isCrawling = false;
            taczfixes$wasParCoolCrawling = false;
        }
    }

    @Inject(method = "setCrawlPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSetCrawlPose(CallbackInfo ci) {
        if (ParCoolHelper.isCrawling(shooter)) {
            ci.cancel();
        }
    }
}
