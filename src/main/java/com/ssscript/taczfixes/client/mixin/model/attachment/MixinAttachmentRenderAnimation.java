package com.ssscript.taczfixes.client.mixin.model.attachment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.attachment.AttachmentAnimationManager;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.functional.AttachmentRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 在 TaCZ 的配件渲染入口（最内层 renderAttachment 重载，所有重载最终都会走到这里）前后
 * 应用/清理配件动画变换。
 *
 * <pre>renderAttachment(ItemStack, ItemStack, AttachmentType, PoseStack, ItemDisplayContext, int, int, MultiBufferSource, Consumer)</pre>
 */
@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(value = {AttachmentRender.class}, remap = false)
public abstract class MixinAttachmentRenderAnimation {
    private static final String RENDER_ATTACHMENT =
            "renderAttachment(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lcom/tacz/guns/api/item/attachment/AttachmentType;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;IILnet/minecraft/client/renderer/MultiBufferSource;"
                    + "Ljava/util/function/Consumer;)V";

    @Inject(method = {RENDER_ATTACHMENT}, at = {@At("HEAD")}, remap = false)
    private static void taczfixes$applyAttachmentAnimation(ItemStack attachmentItem, ItemStack gunItem,
                                                           AttachmentType type, PoseStack poseStack,
                                                           ItemDisplayContext transformType, int light, int overlay,
                                                           MultiBufferSource bufferSource,
                                                           Consumer<IFunctionalRenderer> consumer,
                                                           CallbackInfo callback) {
        AttachmentAnimationManager.beforeRender(attachmentItem, gunItem, transformType);
    }

    @Inject(method = {RENDER_ATTACHMENT}, at = {@At("RETURN")}, remap = false)
    private static void taczfixes$clearAttachmentAnimation(ItemStack attachmentItem, ItemStack gunItem,
                                                           AttachmentType type, PoseStack poseStack,
                                                           ItemDisplayContext transformType, int light, int overlay,
                                                           MultiBufferSource bufferSource,
                                                           Consumer<IFunctionalRenderer> consumer,
                                                           CallbackInfo callback) {
        AttachmentAnimationManager.afterRender();
    }
}
