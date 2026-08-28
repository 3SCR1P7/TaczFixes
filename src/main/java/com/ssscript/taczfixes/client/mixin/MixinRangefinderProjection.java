package com.ssscript.taczfixes.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ssscript.taczfixes.client.util.RangefinderDrawBudget;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.tacz.guns.client.resource.pojo.display.gun.RangefinderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 火控瞄具渲染框的锁定与去重:
 * 1. 自定义瞄具槽激活时, 预测框固定在准星中心(渲染 y 直接取锁定值, 不走
 *    getCalibratedYOffset 实时投影, 该投影在自定义槽下会被 handleScopeViewShift 污染而乱飞);
 * 2. 标准与自定义两把火控瞄具同装时, RangefinderManager 全局标志会让两框都绘制,
 *    此处仅在瞄准场景(aimingScene)放行, 备用镜/standby 附件一律抑制, 消除第二个框。
 */
@Mixin(targets = "com.tacz.guns.client.model.functional.RangefinderImpactPredictionRender", remap = false)
public class MixinRangefinderProjection {

    @Inject(method = "lambda$render$0",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$restrictFrameParticle(
            org.joml.Matrix3f normal, Matrix4f pose,
            net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer,
            net.minecraft.world.item.ItemDisplayContext transformType, int light, int overlay,
            CallbackInfo ci) {
        if (!RangefinderDrawBudget.tryConsume()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "lambda$render$0",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/client/model/functional/RangefinderImpactPredictionRender;getCalibratedYOffset(Lorg/joml/Matrix4f;)F",
                    remap = false), remap = false)
    private static float taczfixes$lockedYOffset(Matrix4f pose, Operation<Float> original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.getMainHandItem().isEmpty()) {
            ItemStack gun = mc.player.getMainHandItem();
            if (ScopeSwitchState.getActiveSlot(gun) != null) {
                return RangefinderManager.getImpactProjectionY() * 80.0F;
            }
        }
        return original.call(pose);
    }
}
