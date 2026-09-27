package com.ssscript.taczfixes.client.mixin;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public interface PbrCompositeAccessor {
    @Accessor("state") RenderType.CompositeState taczfixes$pbrState();
}
