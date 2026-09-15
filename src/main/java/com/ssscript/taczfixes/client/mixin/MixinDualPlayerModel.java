package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.DualThirdPersonArmPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {PlayerModel.class}, priority = 800)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinDualPlayerModel.class */
public abstract class MixinDualPlayerModel<T extends LivingEntity> extends HumanoidModel<T> {

    @Shadow
    @Final
    public ModelPart leftSleeve;

    protected MixinDualPlayerModel(ModelPart root) {
        super(root);
    }

    @Inject(method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"}, at = {@At("TAIL")})
    private void dualWield$mirrorPlayerArm(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        if (DualThirdPersonArmPose.mirrorRightArm(entity, this.head, this.rightArm, this.leftArm)) {
            this.leftSleeve.copyFrom(this.leftArm);
        }
    }
}
