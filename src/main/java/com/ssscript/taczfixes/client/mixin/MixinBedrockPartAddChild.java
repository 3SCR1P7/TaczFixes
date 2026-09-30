package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BedrockPart.class)
public abstract class MixinBedrockPartAddChild {
    @Inject(method = "addChild", at = @At("TAIL"), remap = false)
    private void taczfixes$linkRotatedCubeChild(BedrockPart child, CallbackInfo ci) {
        if (child.name == null && child.getParent() == null) {
            ((MixinBedrockPartParentAccessor) child).taczfixes$setParent((BedrockPart) (Object) this);
        }
    }
}
