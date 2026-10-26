package com.ssscript.taczfixes.client.mixin.model;

import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BedrockPart.class)
public interface MixinBedrockPartParentAccessor {
    @Accessor("parent")
    void taczfixes$setParent(BedrockPart parent);
}
