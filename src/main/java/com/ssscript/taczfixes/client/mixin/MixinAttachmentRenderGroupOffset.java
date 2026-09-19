package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.util.AttachmentGroupOffsetHelper;
import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.functional.AttachmentRender;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/** 配件模型实际绘制的唯一入口, 在这里应用 group_offset 与 pos_alter, 保证所有渲染路径只应用一次。 */
@Mixin(AttachmentRender.class)
public abstract class MixinAttachmentRenderGroupOffset {

    @Inject(method = "lambda$renderAttachment$0", at = @At("HEAD"), remap = false)
    private static void taczfixes$applyAttachmentOffset(ItemStack attachmentStack, ItemStack gunStack,
                                                        AttachmentType type, ResourceLocation id, PoseStack poseStack,
                                                        ItemDisplayContext displayContext, int light, int overlay,
                                                        MultiBufferSource.BufferSource bufferSource,
                                                        ClientAttachmentIndex attachmentIndex, CallbackInfo ci) {
        if (type == null || gunStack == null || gunStack.isEmpty()) return;
        if (attachmentIndex != null) {
            com.tacz.guns.client.model.BedrockAttachmentModel attachmentModel = attachmentIndex.getAttachmentModel();
            if (attachmentModel != null) {
                boolean scope = attachmentIndex.isScope();
                attachmentModel.setIsScope(scope);
                attachmentModel.setIsSight(!scope && attachmentIndex.isSight());
            }
        }
        String slotKey = type.name().toLowerCase(Locale.ROOT);
        AttachmentGroupOffsetHelper.applyForGun(poseStack, gunStack, slotKey);
        float z = PosAlterStorage.get(gunStack, slotKey);
        if (z != 0.0F) {
            poseStack.translate(0.0F, 0.0F, z / 16.0F);
        }
    }
}
