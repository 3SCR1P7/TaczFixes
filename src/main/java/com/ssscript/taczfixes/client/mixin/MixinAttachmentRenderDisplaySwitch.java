package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.util.SwitchedDisplayManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.functional.AttachmentRender;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(AttachmentRender.class)
public abstract class MixinAttachmentRenderDisplaySwitch {

    @Unique
    private static ItemStack taczfixes$renderGun = ItemStack.EMPTY;

    @Inject(method = "renderAttachment", at = @At("HEAD"), remap = false)
    private static void taczfixes$captureGun(ItemStack attachment, ItemStack gun, PoseStack poseStack,
                                             ItemDisplayContext displayContext, int light, int overlay,
                                             CallbackInfo ci) {
        taczfixes$renderGun = gun;
    }

    @Inject(method = "renderAttachment", at = @At("RETURN"), remap = false)
    private static void taczfixes$releaseGun(ItemStack attachment, ItemStack gun, PoseStack poseStack,
                                             ItemDisplayContext displayContext, int light, int overlay,
                                             CallbackInfo ci) {
        taczfixes$renderGun = ItemStack.EMPTY;
    }

    @Redirect(method = "renderAttachment",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/api/TimelessAPI;getClientAttachmentIndex(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
                    remap = false), remap = false)
    private static Optional<ClientAttachmentIndex> taczfixes$resolveIndex(ResourceLocation attachmentId) {
        ItemStack gun = taczfixes$renderGun;
        if (gun != null && !gun.isEmpty()) {
            Optional<ClientAttachmentIndex> alt = SwitchedDisplayManager.getClientAttachmentIndex(gun, attachmentId);
            if (alt.isPresent()) {
                return alt;
            }
        }
        return TimelessAPI.getClientAttachmentIndex(attachmentId);
    }
}