package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.DualWieldAnimatorContext;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"com.tacz.guns.client.resource.GunDisplayInstance"}, remap = false)
/* 双持时把 minigun 类型动画的枪械按普通枪械处理 (第三人称 player_animation 资源换成 rifle_default)。 */
public abstract class MixinGunDisplayInstancePlayerAnimator {
    private static final ResourceLocation TACZFIXES$RIFLE_DEFAULT_PLAYER_ANIMATION = new ResourceLocation("tacz", "rifle_default.player_animation");

    private static final String TACZFIXES$MINIGUN = "minigun";
    private static final String TACZFIXES$DEFAULT_THIRD_PERSON_ANIMATION = "default";

    @Shadow
    @Final
    private ResourceLocation playerAnimator3rd;

    @Shadow
    private String thirdPersonAnimation;

    @Inject(method = {"getPlayerAnimator3rd"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private void taczfixes$swapMinigunPlayerAnimation(CallbackInfoReturnable<ResourceLocation> callback) {
        if (DualWieldAnimatorContext.isDualWielding() && this.playerAnimator3rd != null && this.playerAnimator3rd.getPath().toLowerCase(Locale.ROOT).contains(TACZFIXES$MINIGUN)) {
            callback.setReturnValue(TACZFIXES$RIFLE_DEFAULT_PLAYER_ANIMATION);
        }
    }

    @Inject(method = {"getThirdPersonAnimation"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private void taczfixes$swapMinigunThirdPersonAnimation(CallbackInfoReturnable<String> callback) {
        if (DualWieldAnimatorContext.isDualWielding() && this.thirdPersonAnimation != null && this.thirdPersonAnimation.toLowerCase(Locale.ROOT).contains(TACZFIXES$MINIGUN)) {
            callback.setReturnValue(TACZFIXES$DEFAULT_THIRD_PERSON_ANIMATION);
        }
    }
}
