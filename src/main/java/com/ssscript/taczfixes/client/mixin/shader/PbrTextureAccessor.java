package com.ssscript.taczfixes.client.mixin.shader;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.Optional;

@Mixin(RenderStateShard.EmptyTextureStateShard.class)
public interface PbrTextureAccessor {
    @Invoker(value = "cutoutTexture", remap = false) Optional<ResourceLocation> taczfixes$pbrTextureLocation();
}
