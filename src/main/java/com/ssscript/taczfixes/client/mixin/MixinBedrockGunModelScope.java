package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {BedrockGunModel.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinBedrockGunModelScope.class */
public abstract class MixinBedrockGunModelScope {
    @Redirect(method = {"lambda$render$30"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/resource/index/ClientAttachmentIndex;isScope()Z"), require = ServerMessageOffhandActionResult.ACTION_BOLT)
    private static boolean dualWield$disableOffhandScopeStencil(ClientAttachmentIndex attachment) {
        return DualRenderContext.getPhase() != DualRenderContext.HandPhase.OFFHAND && attachment.isScope();
    }

    @Redirect(method = {"lambda$renderAccelerated$31"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/resource/index/ClientAttachmentIndex;isScope()Z"), require = ServerMessageOffhandActionResult.ACTION_BOLT)
    private static boolean dualWield$disableAcceleratedOffhandScopeStencil(ClientAttachmentIndex attachment) {
        return DualRenderContext.getPhase() != DualRenderContext.HandPhase.OFFHAND && attachment.isScope();
    }

    @ModifyArg(method = {"renderAccelerated"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/compat/ar/ARCompat;setRenderBeforeFunction(Ljava/lang/Runnable;)V", remap = false), index = ServerMessageOffhandActionResult.ACTION_SHOOT, require = ServerMessageOffhandActionResult.ACTION_RELOAD, allow = ServerMessageOffhandActionResult.ACTION_RELOAD, remap = false)
    private Runnable dualWield$freezeOffhandScopeStencilDecision(Runnable original) {
        if (DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND) {
            return MixinBedrockGunModelScope::dualWield$noOp;
        }
        return original;
    }

    private static void dualWield$noOp() {
    }
}
