package com.ssscript.taczfixes.client.mixin.renderer;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(value = GunItemRendererWrapper.class, remap = false)
public abstract class MixinGunItemRendererWrapperTransparency {

    @Redirect(method = "lambda$renderByItem$6",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;entityCutout(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"),
            require = 0, remap = false)
    private static RenderType taczfixes$useTranslucentGunRenderType(ResourceLocation texture) {
        return Config.GUN_TRANSLUCENT_RENDER.get() && com.ssscript.taczfixes.client.util.GunTransparency.needsTranslucent(texture)
                ? RenderType.entityTranslucent(texture)
                : RenderType.entityCutout(texture);
    }
}
