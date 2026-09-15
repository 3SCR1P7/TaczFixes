package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.compat.YsmCompatibilityDiagnostics;
import com.ssscript.taczfixes.common.compat.YsmDualWieldCompat;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.oOoOOO0OOOoo000O0o0oo0OO"}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/compat/MixinYsmRendererPose.class */
public abstract class MixinYsmRendererPose {
    @Inject(method = {"Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/o0000OoOooO0oo0o0oooo0Oo;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at = {@At(value = "INVOKE_ASSIGN", target = "Lcom/elfmcys/yesstevemodel/o0000OoOooO0oo0o0oooo0Oo;o0OOooo0o0OO00OoOOOo0o0O(F)Lcom/elfmcys/yesstevemodel/OO00O0o0OooOOOo00OO00o00;", ordinal = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)}, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private void taczDualWield$mirrorPoseBeforeMesh(@Coerce Object animatable, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo callbackInfo) throws ReflectiveOperationException, IllegalArgumentException {
        if (YsmDualWieldCompat.mirrorFinalPose(animatable) != null) {
            YsmCompatibilityDiagnostics.markPreMeshPoseHook();
        }
    }
}
