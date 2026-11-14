package com.ssscript.taczfixes.client.mixin.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.compat.YsmCompatibilityDiagnostics;
import com.ssscript.taczfixes.common.compat.YsmDualWieldCompat;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * YSM 通用实体渲染器的网格生成入口 (1.21.1 NeoForge 构建: com.elfmcys.yesstevemodel.oO0OoOOOoO0o0Oo00ooOOoOO)。
 * 与 {@link MixinYsmLivingRendererPose} 相同, 在网格生成前镜像手臂姿势。
 */
@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.oO0OoOOOoO0o0Oo00ooOOoOO"}, remap = false)
public abstract class MixinYsmRendererPose {
    @Inject(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/OoO0oo0o0o0oOoo0oOOO0Ooo;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
            at = {@At(
                    value = "INVOKE_ASSIGN",
                    target = "Lcom/elfmcys/yesstevemodel/OoO0oo0o0o0oOoo0oOOO0Ooo;oOoo00O0o0oO0o0oO00OO0O0(F)Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;",
                    ordinal = ServerMessageOffhandActionResult.ACTION_SHOOT,
                    remap = false)},
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private void taczDualWield$mirrorPoseBeforeMesh(@Coerce Object animatable, float entityYaw, float partialTick,
                                                    PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                    CallbackInfo callbackInfo) throws ReflectiveOperationException, IllegalArgumentException {
        if (YsmDualWieldCompat.mirrorFinalPose(animatable) != null) {
            YsmCompatibilityDiagnostics.markPreMeshPoseHook();
        }
    }
}
