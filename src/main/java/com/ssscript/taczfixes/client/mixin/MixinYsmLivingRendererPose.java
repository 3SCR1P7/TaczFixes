package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.compat.YsmCompatibilityDiagnostics;
import com.ssscript.taczfixes.common.compat.YsmDualWieldCompat;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.OOoo0o0oO000ooO0Oo00OoOo"}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/compat/MixinYsmLivingRendererPose.class */
public abstract class MixinYsmLivingRendererPose {
    @Inject(method = {"Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/o0O0oOooOo0OoOo0oOo00O00;Lnet/minecraft/resources/ResourceLocation;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at = {@At(value = "INVOKE_ASSIGN", target = "Lcom/elfmcys/yesstevemodel/o0O0oOooOo0OoOo0oOo00O00;o0OOooo0o0OO00OoOOOo0o0O(F)Lcom/elfmcys/yesstevemodel/OO00O0o0OooOOOo00OO00o00;", ordinal = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)}, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private void taczDualWield$mirrorLivingPoseBeforeMesh(@Coerce Object animatable, ResourceLocation textureOverride, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo callbackInfo) throws ReflectiveOperationException, IllegalArgumentException {
        if (YsmDualWieldCompat.mirrorFinalPose(animatable) != null) {
            YsmCompatibilityDiagnostics.markPreMeshPoseHook();
        }
    }
}
