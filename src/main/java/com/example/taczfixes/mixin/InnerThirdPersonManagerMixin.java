package com.example.taczfixes.mixin;

import com.example.taczfixes.util.ParCoolHelper;
import com.tacz.guns.client.animation.third.InnerThirdPersonManager;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InnerThirdPersonManager.class, remap = false)
public class InnerThirdPersonManagerMixin {

    @Inject(method = "setRotationAnglesHead", at = @At("HEAD"), cancellable = true)
    private static void onSetRotationAnglesHead(LivingEntity entityIn, ModelPart rightArm, ModelPart leftArm, ModelPart body, ModelPart head, float limbSwingAmount, CallbackInfo ci) {
        if (ParCoolHelper.isCrawling(entityIn)) {
            PlayerAnimatorCompat.stopAllAnimation(entityIn);
            ci.cancel();
        }
    }
}
