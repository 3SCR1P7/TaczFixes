package com.ssscript.taczfixes.client.client;

import com.tacz.guns.api.client.other.ThirdPersonManager;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** 双持时把 minigun 类型动画的枪械按普通步枪处理: 第三人称玩家动画、持枪姿势都换成 default 一套。 */
@OnlyIn(Dist.CLIENT)
public final class DualWieldMinigunAnimation {

    /** 普通步枪使用的第三人称 player_animation 资源。 */
    public static final ResourceLocation RIFLE_DEFAULT_PLAYER_ANIMATION =
            ResourceLocation.fromNamespaceAndPath("tacz", "rifle_default.player_animation");
    /** 普通步枪使用的第三人称硬编码姿势名。 */
    public static final String DEFAULT_THIRD_PERSON_ANIMATION = "default";

    private static final String MINIGUN = "minigun";
    /** 用于判断 ThirdPersonManager 是否已经注册过内置动画(未初始化时任何名字都会返回 default)。 */
    private static final String UNREGISTERED_PROBE = "taczfixes$unregistered";

    private DualWieldMinigunAnimation() {
    }

    /** 该第三人称动画是否就是 TaCZ 内置的 minigun 姿势(名字带 minigun, 或注册后实际实现相同)。 */
    public static boolean isMinigunThirdPersonAnimation(String animation) {
        if (animation == null || animation.isEmpty()) {
            return false;
        }
        if (animation.toLowerCase(Locale.ROOT).contains(MINIGUN)) {
            return true;
        }
        Object minigun = ThirdPersonManager.getAnimation(MINIGUN);
        if (minigun == ThirdPersonManager.getAnimation(UNREGISTERED_PROBE)) {
            // 尚未注册内置动画, 只能靠名字判断
            return false;
        }
        return minigun == ThirdPersonManager.getAnimation(animation);
    }

    /** player_animator 资源是否为 minigun 专用动画。 */
    public static boolean isMinigunPlayerAnimation(ResourceLocation location) {
        return location != null && location.getPath().toLowerCase(Locale.ROOT).contains(MINIGUN);
    }

    /**
     * 当前显示数据是否需要在双持期间按普通枪械渲染。
     * 双持上下文由 {@link DualWieldAnimatorContext} 提供, 非双持时一律返回 false。
     */
    public static boolean isMinigun(String thirdPersonAnimation, ResourceLocation playerAnimator3rd) {
        if (!DualWieldAnimatorContext.isDualWielding()) {
            return false;
        }
        return isMinigunThirdPersonAnimation(thirdPersonAnimation) || isMinigunPlayerAnimation(playerAnimator3rd);
    }
}
