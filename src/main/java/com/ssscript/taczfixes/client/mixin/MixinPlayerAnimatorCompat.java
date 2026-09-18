package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.DualWieldAnimatorContext;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat"}, remap = false)
/* 标记当前第三人称动画请求是否来自双持实体, 供 GunDisplayInstance 替换动画资源。 */
public abstract class MixinPlayerAnimatorCompat {

    @Inject(method = {"hasPlayerAnimator3rd"}, at = {@At("HEAD")}, remap = false)
    private static void taczfixes$beginDualWieldAnimatorContext(LivingEntity entity, GunDisplayInstance display, CallbackInfoReturnable<Boolean> callback) {
        DualWieldAnimatorContext.begin(DualWieldEligibility.isDualWielding(entity));
    }

    @Inject(method = {"hasPlayerAnimator3rd"}, at = {@At("RETURN")}, remap = false)
    private static void taczfixes$endDualWieldAnimatorContext(LivingEntity entity, GunDisplayInstance display, CallbackInfoReturnable<Boolean> callback) {
        DualWieldAnimatorContext.end();
    }

    @Inject(method = {"playAnimation"}, at = {@At("HEAD")}, remap = false)
    private static void taczfixes$beginDualWieldAnimatorContextForPlay(LivingEntity entity, GunDisplayInstance display, float partialTick, CallbackInfo callback) {
        DualWieldAnimatorContext.begin(DualWieldEligibility.isDualWielding(entity));
    }

    @Inject(method = {"playAnimation"}, at = {@At("RETURN")}, remap = false)
    private static void taczfixes$endDualWieldAnimatorContextForPlay(LivingEntity entity, GunDisplayInstance display, float partialTick, CallbackInfo callback) {
        DualWieldAnimatorContext.end();
    }
}
