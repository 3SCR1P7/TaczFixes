package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.handler.SteplessZoomHandler;
import com.ssscript.taczfixes.util.ScopeSwitchState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public class MixinCameraSetupEventZoom {
    private static float taczfixes$smoothedZoom = -1f;
    private static long taczfixes$lastZoomMillis = -1L;

    @Redirect(method = {
            "applyScopeMagnification",
            "initialCameraRecoil",
            "applyGunModelFovModifying"
    }, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IGun;getAimingZoom(Lnet/minecraft/world/item/ItemStack;)F"), remap = false)
    private static float taczfixes$aimingZoom(IGun gun, ItemStack stack) {
        float zoom = ScopeSwitchState.aimingZoom(gun, stack);
        if (taczfixes$smoothedZoom < 0f) {
            taczfixes$smoothedZoom = zoom;
            taczfixes$lastZoomMillis = System.currentTimeMillis();
        } else {
            long now = System.currentTimeMillis();
            long delta = now - taczfixes$lastZoomMillis;
            if (delta >= 1L) {
                float dt = Math.min(delta / 1000f, 0.05f);
                if (zoom > taczfixes$smoothedZoom) {
                    taczfixes$smoothedZoom += (zoom - taczfixes$smoothedZoom)
                            * (1f - (float) Math.exp(-dt * 12f));
                } else {
                    taczfixes$smoothedZoom += (zoom - taczfixes$smoothedZoom)
                            * (1f - (float) Math.exp(-dt * 60f));
                }
                taczfixes$lastZoomMillis = now;
            }
        }
        float stepless = SteplessZoomHandler.getSteplessZoom(stack);
        if (stepless > 0.0f) {
            return stepless;
        }
        return taczfixes$smoothedZoom;
    }

    @Redirect(method = "applyGunModelFovModifying",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IGun;getAttachmentId(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/api/item/attachment/AttachmentType;)Lnet/minecraft/resources/ResourceLocation;"), remap = false)
    private static ResourceLocation taczfixes$scopeId(IGun gun, ItemStack stack, AttachmentType type) {
        return ScopeSwitchState.attachmentId(gun, stack, type);
    }

    @Redirect(method = "applyGunModelFovModifying",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IGun;getAttachmentTag(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/api/item/attachment/AttachmentType;)Lnet/minecraft/nbt/CompoundTag;"), remap = false)
    private static CompoundTag taczfixes$scopeTag(IGun gun, ItemStack stack, AttachmentType type) {
        return ScopeSwitchState.attachmentTag(gun, stack, type);
    }
}