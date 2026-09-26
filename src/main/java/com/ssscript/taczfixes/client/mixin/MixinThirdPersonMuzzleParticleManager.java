package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.DualMuzzleFlashState;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import com.tacz.guns.client.particle.ThirdPersonMuzzleParticleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TaCZ 的第三人称枪口火焰按"实体"生成: 双持时两只手的模型谁先渲染就会把火焰生到谁身上,
 * 与真正开火的手无关。这里只允许"最近开火的那只手"的模型生成火焰。
 * 开火手的记录由本地预测(主手开火成功 / playOffhandShotVisual)写入, 不依赖网络同步的物品比较。
 */
@Mixin(value = ThirdPersonMuzzleParticleManager.class, remap = false)
public abstract class MixinThirdPersonMuzzleParticleManager {

    @Inject(method = "onMuzzleRendered", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dualWield$gateThirdPersonFlashByHand(LivingEntity entity, Matrix4f muzzleMatrix, CallbackInfo callback) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || entity != player || !DualWieldClient.isDualMode(player)) {
            return;
        }
        DualRenderContext.HandPhase phase = DualRenderContext.getPhase();
        if (phase != DualRenderContext.HandPhase.MAIN && phase != DualRenderContext.HandPhase.OFFHAND) {
            return;
        }
        DualRenderContext.HandPhase firingHand = DualWieldClient.getThirdPersonFlashHand();
        if (firingHand == DualRenderContext.HandPhase.NONE || !DualWieldClient.isThirdPersonFlashHandFresh(1000L)) {
            return;
        }
        // 正在渲染的手不是最近开火的那只手时, 不生成第三人称枪口火焰
        if (phase != firingHand) {
            callback.cancel();
        }
    }
}
