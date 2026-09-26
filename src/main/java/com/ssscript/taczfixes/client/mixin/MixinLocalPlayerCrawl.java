package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.ParCoolHelper;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerCrawl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ParCool 爬行时保留其移动物理, 但爬行姿势/动画交给 TaCZ:
 * 仅在 ParCool 爬行且手持可爬行枪械时强制 TaCZ 爬行标记, 随后由原版
 * tickCrawl 继续执行(检查与姿势设置); 结束时清除标记并让原版清除姿势。
 */
@Mixin(LocalPlayerCrawl.class)
public class MixinLocalPlayerCrawl {
    @Shadow(remap = false)
    private LocalPlayer player;
    @Shadow(remap = false)
    private boolean isCrawling;

    @Unique
    private boolean taczfixes$wasParCoolCrawling;

    @Inject(method = "tickCrawl", at = @At("HEAD"), remap = false)
    private void onTickCrawl(CallbackInfo ci) {
        if (ParCoolHelper.isCrawling(player) && taczfixes$canTaCzCrawl(player)) {
            isCrawling = true;
            taczfixes$wasParCoolCrawling = true;
        } else if (taczfixes$wasParCoolCrawling) {
            isCrawling = false;
            taczfixes$wasParCoolCrawling = false;
        }
    }

    /**
     * ParCool 爬行时不设置 TaCZ 的强制姿势: 强制姿势会让 ParCool 的
     * isVisuallyCrawling() 判定为真, 导致松开爬行键也无法停止爬行。
     * 爬行姿势由 ParCool 自身每 tick 设置, TaCZ 动画仍可正常工作。
     */
    @Inject(method = "setCrawlPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSetCrawlPose(CallbackInfo ci) {
        if (ParCoolHelper.isCrawling(player) && taczfixes$canTaCzCrawl(player)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean taczfixes$canTaCzCrawl(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null && gun.isCanCrawl(stack);
    }
}
