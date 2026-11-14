package com.ssscript.taczfixes.client.mixin.compat;

import com.ssscript.taczfixes.client.client.DualWieldAnimatorContext;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import java.util.Optional;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(targets = {"com.tacz.guns.compat.playeranimator.animation.AnimationManager"}, remap = false)
/* player_animator 的第三人称动画(每帧更新、开火/换弹/近战的一次性与循环动画)按双持上下文替换 minigun 动画资源。 */
public abstract class MixinPlayerAnimatorAnimationManager {

    private static final java.util.Map<AbstractClientPlayer, Integer> taczfixes$offhandAnimTick =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** 主手不是枪、副手是枪时, 直接走 TaCZ 的 playeranimator 播放(与双手持枪同一函数/资源), 否则原逻辑会 stopAll。 */
    @Inject(method = {"updateAnimation"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private static void taczfixes$offhandOnlyAnimation(AbstractClientPlayer player, CallbackInfo callback) {
        net.minecraft.world.item.ItemStack main = player.getMainHandItem();
        if (IGun.getIGunOrNull(main) != null) {
            return;
        }
        Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(player.getOffhandItem());
        if (display.isEmpty()) {
            return;
        }
        Integer previous = taczfixes$offhandAnimTick.get(player);
        if (previous != null && previous == player.tickCount) {
            callback.cancel();
            return;
        }
        taczfixes$offhandAnimTick.put(player, player.tickCount);
        try {
            com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat.playAnimation(player, display.get(),
                    player.walkAnimation.speed());
        } catch (Throwable ignored) {
        }
        callback.cancel();
    }
    @Inject(method = {"updateAnimation"}, at = {@At("HEAD")}, remap = false)
    private static void taczfixes$beginDualWieldContextUpdate(AbstractClientPlayer player,
                                                              CallbackInfo callback) {
        DualWieldAnimatorContext.begin(DualWieldEligibility.isDualWielding(player));
    }

    @Inject(method = {"updateAnimation"}, at = {@At("RETURN")}, remap = false)
    private static void taczfixes$endDualWieldContextUpdate(AbstractClientPlayer player,
                                                            CallbackInfo callback) {
        DualWieldAnimatorContext.end();
    }

    @Inject(method = {"playOnceAnimation"}, at = {@At("HEAD")}, remap = false)
    private static void taczfixes$beginDualWieldContextOnce(AbstractClientPlayer player, GunDisplayInstance display,
                                                            ResourceLocation dataId, String animationName,
                                                            CallbackInfo callback) {
        DualWieldAnimatorContext.begin(DualWieldEligibility.isDualWielding(player));
    }

    @Inject(method = {"playOnceAnimation"}, at = {@At("RETURN")}, remap = false)
    private static void taczfixes$endDualWieldContextOnce(AbstractClientPlayer player, GunDisplayInstance display,
                                                          ResourceLocation dataId, String animationName,
                                                          CallbackInfo callback) {
        DualWieldAnimatorContext.end();
    }

    @Inject(method = {"playLoopAnimation"}, at = {@At("HEAD")}, remap = false)
    private static void taczfixes$beginDualWieldContextLoop(AbstractClientPlayer player, GunDisplayInstance display,
                                                            ResourceLocation dataId, String animationName,
                                                            CallbackInfo callback) {
        DualWieldAnimatorContext.begin(DualWieldEligibility.isDualWielding(player));
    }

    @Inject(method = {"playLoopAnimation"}, at = {@At("RETURN")}, remap = false)
    private static void taczfixes$endDualWieldContextLoop(AbstractClientPlayer player, GunDisplayInstance display,
                                                          ResourceLocation dataId, String animationName,
                                                          CallbackInfo callback) {
        DualWieldAnimatorContext.end();
    }
}
