package com.ssscript.taczfixes.client.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.script.ModelRendererWrapper;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.ssscript.taczfixes.common.compat.TouhouMaidDualWieldAnimation;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import java.util.HashMap;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"com.github.tartaricacid.touhoulittlemaid.client.animation.HardcodedAnimationManger"}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/compat/MixinTouhouMaidAnimationManager.class */
public abstract class MixinTouhouMaidAnimationManager {
    @Inject(method = {"playMaidAnimation(Lcom/github/tartaricacid/touhoulittlemaid/api/entity/IMaid;Ljava/util/HashMap;FFFFF)V"}, at = {@At("TAIL")}, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private static void taczDualWield$mirrorBedrockFinalPose(IMaid maid, HashMap<String, ModelRendererWrapper> models, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callbackInfo) {
        Mob entity = maid == null ? null : maid.asEntity();
        TouhouMaidDualWieldAnimation.mirrorBedrockPose(entity, models);
    }

    @Inject(method = {"playGeckoMaidAnimation(Lcom/github/tartaricacid/touhoulittlemaid/api/entity/IMaid;Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/geo/animated/AnimatedGeoModel;FFFFF)V"}, at = {@At("TAIL")}, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private static void taczDualWield$mirrorGeckoFinalPose(IMaid maid, AnimatedGeoModel model, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callbackInfo) {
        Mob entity = maid == null ? null : maid.asEntity();
        TouhouMaidDualWieldAnimation.mirrorGeckoPose(entity, model);
    }
}
