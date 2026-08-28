package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.util.AttachmentGroupOffsetHelper;
import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.Locale;

@Mixin(BedrockGunModel.class)
public abstract class MixinBedrockGunModelScopeGroupOffset {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", at = @At(value = "FIELD",
            target = "Lcom/tacz/guns/client/model/BedrockGunModel;scopePosPath:Ljava/util/List;",
            remap = false), remap = false)
    private void taczfixes$applyScopeGroupOffset(PoseStack pose, ItemStack itemStack,
                                                 ItemDisplayContext displayContext, RenderType renderType,
                                                 int light, int overlay, float red, float green, float blue, float alpha,
                                                 net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
        applyOffset(pose);
    }

    @Inject(method = "renderAccelerated(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IILnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", at = @At(value = "FIELD",
            target = "Lcom/tacz/guns/client/model/BedrockGunModel;scopePosPath:Ljava/util/List;",
            remap = false), remap = false)
    private void taczfixes$applyScopeGroupOffsetAccelerated(PoseStack pose, ItemStack itemStack,
                                                            ItemDisplayContext displayContext, RenderType renderType,
                                                            int light, int overlay, net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
        applyOffset(pose);
    }

    private void applyOffset(PoseStack pose) {
        EnumMap<AttachmentType, ItemStack> attachments =
                ((BedrockGunModel) (Object) this).getCurrentAttachmentItem();
        if (attachments == null) return;
        AttachmentGroupOffsetHelper.apply(pose, attachments, AttachmentType.SCOPE.name().toLowerCase(Locale.ROOT));
        ItemStack gunStack = ((BedrockGunModel) (Object) this).getCurrentGunItem();
        if (gunStack != null && !gunStack.isEmpty()) {
            float z = PosAlterStorage.get(gunStack, "scope");
            if (z != 0.0F) {
                pose.translate(0.0F, 0.0F, z / 16.0F);
            }
        }
    }
}