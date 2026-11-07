package com.ssscript.taczfixes.client.mixin.renderer;

import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemInHandRenderer.class)
public interface MixinItemInHandRendererHeightAccessor {
    @Accessor(value = "offHandHeight", remap = false)
    void taczfixes$setOffHandHeight(float height);

    @Accessor(value = "oOffHandHeight", remap = false)
    void taczfixes$setOOffHandHeight(float height);
}
