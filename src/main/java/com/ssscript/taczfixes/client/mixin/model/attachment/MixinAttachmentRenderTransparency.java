package com.ssscript.taczfixes.client.mixin.model.attachment;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.client.model.functional.AttachmentRender;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AttachmentRender.class, remap = false)
public abstract class MixinAttachmentRenderTransparency {
    private static final String RENDER_ATTACHMENT_LAMBDA =
            "lambda$renderAttachment$1(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
            + "Lcom/tacz/guns/api/item/attachment/AttachmentType;Lnet/minecraft/resources/ResourceLocation;"
            + "Ljava/util/function/Consumer;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II"
            + "Lcom/tacz/guns/client/resource/index/ClientAttachmentIndex;)V";

    @Redirect(method = RENDER_ATTACHMENT_LAMBDA,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;entityCutout(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"),
            require = 0, remap = true)
    private static RenderType taczfixes$useTranslucentAttachmentRenderType(ResourceLocation texture) {
        return Config.GUN_TRANSLUCENT_RENDER.get() && com.ssscript.taczfixes.client.util.GunTransparency.needsTranslucent(texture)
                ? RenderType.entityTranslucent(texture)
                : RenderType.entityCutout(texture);
    }
}
