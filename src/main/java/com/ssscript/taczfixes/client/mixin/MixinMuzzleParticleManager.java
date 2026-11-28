package com.ssscript.taczfixes.client.mixin;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.firstperson.FirstPersonParticleSystem;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.util.MuzzleAfterSmoke;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.particle.MuzzleParticleManager;
import com.ssscript.taczfixes.client.util.dual.DualMuzzleParticleContext;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;
import net.minecraft.world.InteractionHand;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {MuzzleParticleManager.class}, remap = false)
public abstract class MixinMuzzleParticleManager {
    @ModifyVariable(method = "spawnAndBind", at = @At("HEAD"), argsOnly = true)
    private static List<ParticleEffectData> taczfixes$addAfterSmoke(
            List<ParticleEffectData> particles, FirstPersonParticleSystem system, PoseStack poseStack,
            BedrockGunModel model, InteractionHand hand) {
        if (model.getMuzzleFlashPosPath() == null || MuzzleParticleManager.getBasePose(hand) == null) {
            return particles;
        }
        return MuzzleAfterSmoke.withFirstPersonSmoke(particles, hand);
    }

    @WrapOperation(method = "spawnAndBind", at = @At(value = "INVOKE",
            target = "Lcom/github/mcmodderanchor/simplebedrockmodel/v1/particle/runtime/ParticleEmitterInstance;setEmitterTransform(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private static void taczfixes$moveAfterSmokeWithMuzzle(ParticleEmitterInstance emitter, Matrix4f handTransform,
            Matrix4f worldTransform, Operation<Void> original, @Local(argsOnly = true) InteractionHand hand) {
        MuzzleAfterSmoke.moveFirstPersonSmoke(emitter, handTransform, hand);
        original.call(emitter, handTransform, worldTransform);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "enqueueShoot", at = @At(value = "INVOKE", target = "Ljava/util/EnumMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private static Object dualWield$useOffhandKeyForOffhandShots(EnumMap pending, Object mainHandKey, Function mappingFunction) {
        Object handKey = DualMuzzleParticleContext.isOffhand() ? InteractionHand.OFF_HAND : mainHandKey;
        return pending.computeIfAbsent(handKey, mappingFunction);
    }
}
