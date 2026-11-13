package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.ParCoolHelper;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.LivingEntityCrawl;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TaCZ × ParCool 匍匐兼容（服务端）。
 *
 * <p>与客户端同理：ParCool 匍匐时只维护 TaCZ 的 {@code data.isCrawling} 状态，
 * 取消原版 {@code tickCrawling}，避免它因为强制姿态不是 SWIMMING 而每个 tick 清状态；
 * ParCool 结束时清理状态与残留姿态，不干扰 TaCZ 自己的爬行逻辑。
 */
@Mixin(LivingEntityCrawl.class)
public class MixinLivingEntityCrawl {
    @Shadow(remap = false)
    @Final
    private LivingEntity shooter;

    @Shadow(remap = false)
    @Final
    private ShooterDataHolder data;

    @Unique
    private boolean taczfixes$parCoolCrawling;

    @Inject(method = "tickCrawling", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$tickCrawling(CallbackInfo ci) {
        boolean parCoolCrawl = ParCoolHelper.isCrawling(shooter) && taczfixes$canTaCzCrawl(shooter);
        if (parCoolCrawl) {
            data.isCrawling = true;
            taczfixes$parCoolCrawling = true;
            ci.cancel();
            return;
        }
        if (taczfixes$parCoolCrawling) {
            taczfixes$parCoolCrawling = false;
            data.isCrawling = false;
            if (shooter instanceof Player player && player.getForcedPose() == Pose.SWIMMING) {
                player.setForcedPose(null);
            }
            ci.cancel();
        }
    }

    @Inject(method = "crawl", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$crawl(boolean crawling, CallbackInfo ci) {
        if (ParCoolHelper.isCrawling(shooter) && taczfixes$canTaCzCrawl(shooter)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean taczfixes$canTaCzCrawl(LivingEntity entity) {
        ItemStack stack = entity.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null && gun.isCanCrawl(stack);
    }
}
