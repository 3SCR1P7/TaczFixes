package com.ssscript.taczfixes.client.mixin.model;

import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(BedrockPart.class)
public interface MixinBedrockPartParentAccessor {
    @Accessor(value = "parent", remap = false)
    void taczfixes$setParent(BedrockPart parent);
}
