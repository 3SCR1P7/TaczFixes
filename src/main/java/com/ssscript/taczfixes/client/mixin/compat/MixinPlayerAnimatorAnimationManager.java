package com.ssscript.taczfixes.client.mixin.compat;

import com.ssscript.taczfixes.client.client.DualWieldAnimatorContext;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
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
