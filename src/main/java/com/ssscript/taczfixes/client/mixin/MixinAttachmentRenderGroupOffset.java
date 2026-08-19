package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.common.util.AttachmentGroupOffsetHelper;
import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.functional.AttachmentRender;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.Locale;

@Mixin(AttachmentRender.class)
public abstract class MixinAttachmentRenderGroupOffset {

    @Shadow(remap = false) private BedrockGunModel bedrockGunModel;
    @Shadow(remap = false) private AttachmentType type;

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void taczfixes$applyGroupOffset(PoseStack poseStack, VertexConsumer vertexConsumer,
                                            ItemDisplayContext displayContext, int light, int overlay,
                                            CallbackInfo ci) {
        if (this.bedrockGunModel == null || this.type == null) return;
        EnumMap<AttachmentType, ItemStack> attachments = this.bedrockGunModel.getCurrentAttachmentItem();
        if (attachments == null) return;
        ItemStack slotItem = attachments.get(this.type);
        if (slotItem == null || slotItem.isEmpty()) return;
        AttachmentGroupOffsetHelper.apply(poseStack, attachments, this.type.name().toLowerCase(Locale.ROOT));
        ItemStack gunStack = this.bedrockGunModel.getCurrentGunItem();
        if (gunStack != null && !gunStack.isEmpty()) {
            float z = PosAlterStorage.get(gunStack, this.type.name().toLowerCase(Locale.ROOT));
            if (z != 0.0F) {
                poseStack.translate(0.0F, 0.0F, z / 16.0F);
            }
        }
    }
}