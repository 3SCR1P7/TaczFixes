package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.GunRecolorManager;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 处于配件渲染上下文且该配件有调色数据时, 用重着色贴图替换原始模型贴图。 */
@Mixin(value = ClientAttachmentIndex.class, remap = false)
public class MixinClientAttachmentIndexRecolor {

    @Inject(method = "getModelTexture", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$recolorModelTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation recolored = GunRecolorManager.recolored(cir.getReturnValue());
        if (recolored != null) {
            cir.setReturnValue(recolored);
        }
    }
}
