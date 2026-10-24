package com.ssscript.taczfixes.client.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemInHandRenderer.class)
public interface MixinItemInHandRendererHeightAccessor {
    @Accessor("offHandHeight")
    void taczfixes$setOffHandHeight(float height);

    @Accessor("oOffHandHeight")
    void taczfixes$setOOffHandHeight(float height);
}
