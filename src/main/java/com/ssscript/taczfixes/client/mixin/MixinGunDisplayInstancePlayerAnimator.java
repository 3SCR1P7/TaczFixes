package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.client.DualWieldMinigunAnimation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(targets = {"com.tacz.guns.client.resource.GunDisplayInstance"}, remap = false)
/* 双持时把 minigun 类型动画的枪械按普通枪械处理: 第三人称 player_animation 换 rifle_default, 硬编码姿势换 default, 手臂不再锁定。 */
public abstract class MixinGunDisplayInstancePlayerAnimator {

    @Shadow(remap = false)
    private ResourceLocation playerAnimator3rd;

    @Shadow(remap = false)
    private String thirdPersonAnimation;

    @Inject(method = {"getPlayerAnimator3rd"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private void taczfixes$swapMinigunPlayerAnimation(CallbackInfoReturnable<ResourceLocation> callback) {
        if (this.playerAnimator3rd != null
                && DualWieldMinigunAnimation.isMinigun(this.thirdPersonAnimation, this.playerAnimator3rd)) {
            callback.setReturnValue(DualWieldMinigunAnimation.RIFLE_DEFAULT_PLAYER_ANIMATION);
        }
    }

    @Inject(method = {"getThirdPersonAnimation"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private void taczfixes$swapMinigunThirdPersonAnimation(CallbackInfoReturnable<String> callback) {
        if (DualWieldMinigunAnimation.isMinigun(this.thirdPersonAnimation, this.playerAnimator3rd)) {
            callback.setReturnValue(DualWieldMinigunAnimation.DEFAULT_THIRD_PERSON_ANIMATION);
        }
    }

    /** minigun 的 3rd_fixed_hand 会让 player_animator 的手臂俯仰锁定, 双持按普通枪械放开。 */
    @Inject(method = {"is3rdFixedHand"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private void taczfixes$releaseMinigunFixedHand(CallbackInfoReturnable<Boolean> callback) {
        if (DualWieldMinigunAnimation.isMinigun(this.thirdPersonAnimation, this.playerAnimator3rd)) {
            callback.setReturnValue(false);
        }
    }
}
