package com.ssscript.taczfixes.client.mixin.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.compat.YsmCompatibilityDiagnostics;
import com.ssscript.taczfixes.common.compat.YsmDualWieldCompat;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * YSM 生物渲染器的网格生成入口 (1.21.1 NeoForge 构建: com.elfmcys.yesstevemodel.OO0OOoo0ooooOoO0O0o00Ooo)。
 * 在 YSM 计算完动画事件 (网格姿势来源) 之后、真正提交模型网格之前, 把右臂姿势镜像到左臂。
 */
@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.OO0OOoo0ooooOoO0O0o00Ooo"}, remap = false)
public abstract class MixinYsmLivingRendererPose {
    @Inject(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/O0OOoooOOoOo0O00O0oOoo0O;Lnet/minecraft/resources/ResourceLocation;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
            at = {@At(
                    value = "INVOKE_ASSIGN",
                    target = "Lcom/elfmcys/yesstevemodel/O0OOoooOOoOo0O00O0oOoo0O;oOoo00O0o0oO0o0oO00OO0O0(F)Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;",
                    ordinal = ServerMessageOffhandActionResult.ACTION_SHOOT,
                    remap = false)},
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private void taczDualWield$mirrorLivingPoseBeforeMesh(@Coerce Object animatable,
                                                          ResourceLocation textureOverride, float entityYaw,
                                                          float partialTick, PoseStack poseStack,
                                                          MultiBufferSource buffer, int packedLight,
                                                          CallbackInfo callbackInfo) throws ReflectiveOperationException, IllegalArgumentException {
        if (YsmDualWieldCompat.mirrorFinalPose(animatable) != null) {
            YsmCompatibilityDiagnostics.markPreMeshPoseHook();
        }
    }
}
