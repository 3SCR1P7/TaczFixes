package com.ssscript.taczfixes.client.mixin.renderer;

import com.llamalad7.mixinextras.sugar.Local;
import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 常凪冻结: 渲染被冻结实体时把 partial ticks 固定。
 *
 * <p>实体动画普遍使用 {@code ageInTicks = tickCount + partialTick} 计算待机动作;
 * 只冻结 tick 时 tickCount 不变, 但 partialTick 每帧仍在 0~1 之间变化,
 * 于是动画会在"上一帧"和"下一帧"两个姿态之间反复横跳。这里把被冻结实体的
 * partialTick 固定为 1, 动画姿态彻底静止。</p>
 */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRendererFreezePartial {
    @ModifyVariable(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float taczfixes$freezePartialTick(float partialTicks,
                                              @Local(argsOnly = true, ordinal = 0) LivingEntity entity) {
        if (entity != null && entity.level().isClientSide && FreezeClientState.isEntityFrozen(entity)) {
            return 1.0F;
        }
        return partialTicks;
    }
}