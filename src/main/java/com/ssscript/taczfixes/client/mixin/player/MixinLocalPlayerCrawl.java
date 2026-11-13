package com.ssscript.taczfixes.client.mixin.player;

import com.ssscript.taczfixes.common.util.ParCoolHelper;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerCrawl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TaCZ × ParCool 匍匐兼容（客户端）。
 *
 * <p>ParCool 的匍匐由 ParCool 自己管理动作、移动与姿态，TaCZ 只应同步“正在匍匐”状态供
 * 枪械/配件动画读取。旧实现的问题：
 * <ul>
 *     <li>在 {@code tickCrawl} HEAD 直接写 {@code isCrawling = true}，但仍放行原版方法；</li>
 *     <li>原版发现 {@code player.getForcedPose() != SWIMMING} 就调用 {@code setCrawling(false)}，</li>
 *     <li>而 ParCool 模式下我们又不设置 TaCZ 强制姿态，于是每 tick 先 true 再 false，
 *         状态逐帧抖动，脚架动画反复收起/展开。</li>
 * </ul>
 *
 * <p>重写后：ParCool 匍匐时直接维护 {@code isCrawling} 并取消原版 {@code tickCrawl}
 * （不设置 TaCZ 强制姿态，姿态归 ParCool）；ParCool 结束时清理状态并恢复姿态；
 * 未使用 ParCool 匍匐时完全不影响 TaCZ 自己的爬行键逻辑。
 */
@Mixin(LocalPlayerCrawl.class)
public class MixinLocalPlayerCrawl {
    @Shadow(remap = false)
    private LocalPlayer player;

    @Shadow(remap = false)
    private boolean isCrawling;

    @Shadow(remap = false)
    private int crawCooldownTicks;

    @Unique
    private boolean taczfixes$parCoolCrawling;

    @Inject(method = "tickCrawl", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$tickCrawl(CallbackInfo ci) {
        boolean parCoolCrawl = ParCoolHelper.isCrawling(player) && taczfixes$canTaCzCrawl(player);
        if (parCoolCrawl) {
            // 保留原版冷却计时，但不运行原版 tickCrawl（它会因强制姿态不是 SWIMMING 而清状态）。
            if (crawCooldownTicks > 0) {
                crawCooldownTicks--;
            }
            isCrawling = true;
            taczfixes$parCoolCrawling = true;
            ci.cancel();
            return;
        }
        if (taczfixes$parCoolCrawling) {
            // ParCool 匍匐结束：清理 TaCZ 状态，并清掉可能残留的 TaCZ 强制姿态。
            taczfixes$parCoolCrawling = false;
            isCrawling = false;
            if (player.getForcedPose() == Pose.SWIMMING) {
                player.setForcedPose(null);
            }
            ci.cancel();
        }
    }

    /**
     * ParCool 匍匐期间禁止 TaCZ 爬行键改状态：避免它设置强制 SWIMMING 姿态、
     * 发送网络同步，与 ParCool 的姿态管理冲突。
     */
    @Inject(method = "crawl", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$crawl(boolean crawling, CallbackInfo ci) {
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
