package com.ssscript.taczfixes.client.mixin.compat;

import com.ssscript.taczfixes.client.client.DualWieldAnimatorContext;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(targets = {"com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat"}, remap = false)
/* 标记当前第三人称动画请求是否来自双持实体, 供 GunDisplayInstance 替换动画资源。 */
public abstract class MixinPlayerAnimatorCompat {

    /** 主手不是枪、副手是枪时, 也允许 TaCZ 的第三人称 playeranimator 动画(否则副手开火/换弹动画会被 stopAllAnimation 清掉)。 */
    @Inject(method = {"canPlayAnimation"}, at = {@At("RETURN")}, cancellable = true, remap = false)
    private static void taczfixes$allowOffhandGunAnimation(LivingEntity entity, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) {
            return;
        }
        if (com.tacz.guns.api.item.IGun.getIGunOrNull(entity.getOffhandItem()) == null) {
            return;
        }
        if (entity.getPose() == net.minecraft.world.entity.Pose.SLEEPING || entity.onClimbable()
                || entity.isSwimming() || entity.getPose() == net.minecraft.world.entity.Pose.FALL_FLYING) {
            return;
        }
        callback.setReturnValue(true);
    }
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
