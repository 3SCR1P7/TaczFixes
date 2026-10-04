package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.GunRecolorManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 处于枪械渲染上下文且该物品有调色数据时, 用重着色贴图替换原始模型贴图。 */
@Mixin(value = GunDisplayInstance.class, remap = false)
public class MixinGunDisplayInstanceRecolor {

    @Inject(method = "getModelTexture", at = @At("RETURN"), cancellable = true, remap = false)
    private void taczfixes$recolorModelTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation recolored = GunRecolorManager.recolored(cir.getReturnValue());
        if (recolored != null) {
            cir.setReturnValue(recolored);
        }
    }
}
