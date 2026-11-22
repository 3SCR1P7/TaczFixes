package com.ssscript.taczfixes.client.mixin.compat.tml;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 仿照 YSM 双持的处理: 双持时把 TaCZ 双手持枪动画换成"单手持枪"的 rpg 变体,
 * 再在动画结束后把右臂镜像到左臂, 两条手臂就都变成单手朝前持枪的样子。
 */
@Pseudo
@Mixin(targets = {"com.github.tartaricacid.touhoulittlemaid.compat.gun.tacz.client.GunGeckoAnimation"}, remap = false)
public abstract class MixinTmlGunGeckoAnimation {
    private static final String[] TAC_GUN_ANIMATION_PREFIXES = {
            "tac:climbing:fire:", "tac:hold:fire:", "tac:aim:fire:", "tac:climbing:",
            "tac:reload:", "tac:climb:", "tac:hold:", "tac:aim:", "tac:run:"
    };

    @ModifyVariable(method = {"playAnimation"}, at = @At("HEAD"), argsOnly = true, index = 1, require = 0, remap = false)
    private static String taczDualWield$forceOneHandedAnimation(String animationName, AnimationEvent<?> event,
                                                               String originalName, ILoopType loopType) {
        if (animationName == null || event == null) {
            return animationName;
        }
        if (!(event.getAnimatableEntity() instanceof GeckoMaidEntity<?> geckoMaid)) {
            return animationName;
        }
        IMaid maid = geckoMaid.getMaid();
        Mob entity = maid == null ? null : maid.asEntity();
        if (entity == null || !DualWieldEligibility.isDualWielding(entity)) {
            return animationName;
        }
        for (String prefix : TAC_GUN_ANIMATION_PREFIXES) {
            String idSpecificPrefix = prefix.substring(0, prefix.length() - 1) + "$";
            if (animationName.startsWith(prefix) || animationName.startsWith(idSpecificPrefix)) {
                return prefix + "rpg";
            }
        }
        return animationName;
    }
}
