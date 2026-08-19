package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.SwitchedDisplayManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public abstract class MixinCameraSetupEventDisplaySwitch {

    @Redirect(method = "applyGunModelFovModifying",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/api/TimelessAPI;getClientAttachmentIndex(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
                    remap = false), remap = false)
    private static Optional<ClientAttachmentIndex> taczfixes$resolveFovIndex(ResourceLocation scopeId) {
        ItemStack gun = KeepingItemRenderer.getRenderer().getCurrentItem();
        if (gun != null && !gun.isEmpty()) {
            Optional<ClientAttachmentIndex> alt = SwitchedDisplayManager.getClientAttachmentIndex(gun, scopeId);
            if (alt.isPresent()) {
                return alt;
            }
        }
        return TimelessAPI.getClientAttachmentIndex(scopeId);
    }
}