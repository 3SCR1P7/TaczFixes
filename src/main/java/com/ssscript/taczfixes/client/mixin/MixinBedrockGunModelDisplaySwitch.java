package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.SwitchedDisplayManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(BedrockGunModel.class)
public abstract class MixinBedrockGunModelDisplaySwitch {

    @Shadow
    private ItemStack currentGunItem;

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/api/TimelessAPI;getClientAttachmentIndex(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
                    remap = false), remap = false)
    private Optional<ClientAttachmentIndex> taczfixes$resolveIndex(ResourceLocation attachmentId) {
        ItemStack gun = this.currentGunItem;
        if (gun != null && !gun.isEmpty()) {
            Optional<ClientAttachmentIndex> alt = SwitchedDisplayManager.getClientAttachmentIndex(gun, attachmentId);
            if (alt.isPresent()) {
                return alt;
            }
        }
        return TimelessAPI.getClientAttachmentIndex(attachmentId);
    }
}