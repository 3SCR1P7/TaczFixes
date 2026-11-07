package com.ssscript.taczfixes.client.mixin.dual;

import com.ssscript.taczfixes.client.client.DualThirdPersonArmPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {HumanoidModel.class}, priority = 900)
public abstract class MixinDualHumanoidModel<T extends LivingEntity> {

    @Shadow(aliases = {"f_102810_"})
    @Final
    public ModelPart body;

    @Shadow(aliases = {"f_102808_"})
    @Final
    public ModelPart head;

    @Shadow(aliases = {"f_102812_"})
    @Final
    public ModelPart leftArm;

    @Shadow(aliases = {"f_102811_"})
    @Final
    public ModelPart rightArm;

    @Inject(method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"}, at = {@At("TAIL")})
    private void dualWield$mirrorRightArmAnimation(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        DualThirdPersonArmPose.mirrorRightArm(entity, this.body, this.head, this.rightArm, this.leftArm);
    }
}
