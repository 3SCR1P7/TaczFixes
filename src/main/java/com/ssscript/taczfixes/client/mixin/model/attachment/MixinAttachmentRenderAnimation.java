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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 在 TaCZ 的配件渲染入口前后应用/清理配件动画变换。
 *
 * <p>TaCZ 不同版本的最内层重载不同：1.2.0 为带 {@code Consumer<IFunctionalRenderer>} 的 9 参重载，
 * 而 1025 构建为 8 参重载。两个重载都注入（缺失时 require = 0 不报错），运行时按实际存在的重载
 * 二选一，避免 8 参委托给 9 参时重复推进状态机。</p>
 */
@SuppressWarnings({"target", "mapping", "public-target", "unresolvable-target", "visibility", "constraints", "overwrite", "default-package", "raw-types"})
@Mixin(value = {AttachmentRender.class}, remap = false)
public abstract class MixinAttachmentRenderAnimation {
    private static final String RENDER_ATTACHMENT_8 =
            "renderAttachment(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lcom/tacz/guns/api/item/attachment/AttachmentType;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;II"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;)V";

    private static final String RENDER_ATTACHMENT_9 =
            "renderAttachment(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lcom/tacz/guns/api/item/attachment/AttachmentType;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;II"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;Ljava/util/function/Consumer;)V";

    @Unique
    private static Boolean taczfixes$consumerOverload;

    @Unique
    private static boolean taczfixes$hasConsumerOverload() {
        Boolean cached = taczfixes$consumerOverload;
        if (cached != null) {
            return cached;
        }
        boolean found;
        try {
            AttachmentRender.class.getDeclaredMethod("renderAttachment", ItemStack.class, ItemStack.class,
                    AttachmentType.class, PoseStack.class, ItemDisplayContext.class, int.class, int.class,
                    MultiBufferSource.class, Consumer.class);
            found = true;
        } catch (Throwable ignored) {
            found = false;
        }
        taczfixes$consumerOverload = found;
        return found;
    }

    @Inject(method = {RENDER_ATTACHMENT_9}, at = {@At("HEAD")}, require = 0, remap = false)
    private static void taczfixes$applyAttachmentAnimation9(ItemStack attachmentItem, ItemStack gunItem,
                                                            AttachmentType type, PoseStack poseStack,
                                                            ItemDisplayContext transformType, int light, int overlay,
                                                            MultiBufferSource bufferSource,
                                                            Consumer<IFunctionalRenderer> consumer,
                                                            CallbackInfo callback) {
        AttachmentAnimationManager.beforeRender(attachmentItem, gunItem, transformType);
    }

    @Inject(method = {RENDER_ATTACHMENT_9}, at = {@At("RETURN")}, require = 0, remap = false)
    private static void taczfixes$clearAttachmentAnimation9(ItemStack attachmentItem, ItemStack gunItem,
                                                            AttachmentType type, PoseStack poseStack,
                                                            ItemDisplayContext transformType, int light, int overlay,
                                                            MultiBufferSource bufferSource,
                                                            Consumer<IFunctionalRenderer> consumer,
                                                            CallbackInfo callback) {
        AttachmentAnimationManager.afterRender();
    }

    @Inject(method = {RENDER_ATTACHMENT_8}, at = {@At("HEAD")}, require = 0, remap = false)
    private static void taczfixes$applyAttachmentAnimation8(ItemStack attachmentItem, ItemStack gunItem,
                                                            AttachmentType type, PoseStack poseStack,
                                                            ItemDisplayContext transformType, int light, int overlay,
                                                            MultiBufferSource bufferSource,
                                                            CallbackInfo callback) {
        if (!taczfixes$hasConsumerOverload()) {
            AttachmentAnimationManager.beforeRender(attachmentItem, gunItem, transformType);
        }
    }

    @Inject(method = {RENDER_ATTACHMENT_8}, at = {@At("RETURN")}, require = 0, remap = false)
    private static void taczfixes$clearAttachmentAnimation8(ItemStack attachmentItem, ItemStack gunItem,
                                                            AttachmentType type, PoseStack poseStack,
                                                            ItemDisplayContext transformType, int light, int overlay,
                                                            MultiBufferSource bufferSource,
                                                            CallbackInfo callback) {
        if (!taczfixes$hasConsumerOverload()) {
            AttachmentAnimationManager.afterRender();
        }
    }
}
