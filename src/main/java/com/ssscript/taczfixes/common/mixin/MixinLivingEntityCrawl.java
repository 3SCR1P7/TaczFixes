package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.util.ParCoolHelper;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.LivingEntityCrawl;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ParCool 爬行时保留其移动物理, 但爬行姿势交给 TaCZ(服务端同步强制 SWIMMING):
 * 仅强制/清除爬行标记, 随后由原版 tickCrawling 设置或清除姿势。
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
    private boolean taczfixes$wasParCoolCrawling;

    @Inject(method = "tickCrawling", at = @At("HEAD"), remap = false)
    private void onTickCrawling(CallbackInfo ci) {
        if (ParCoolHelper.isCrawling(shooter) && taczfixes$canTaCzCrawl(shooter)) {
            data.isCrawling = true;
            taczfixes$wasParCoolCrawling = true;
        } else if (taczfixes$wasParCoolCrawling) {
            data.isCrawling = false;
            taczfixes$wasParCoolCrawling = false;
        }
    }

    /**
     * ParCool 爬行时不设置 TaCZ 的强制姿势, 否则 ParCool 会认为玩家处于
     * 视觉爬行状态而忽略爬行键松开; 姿势由 ParCool 自身设置。
     */
    @Inject(method = "setCrawlPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSetCrawlPose(CallbackInfo ci) {
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
