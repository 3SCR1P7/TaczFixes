package com.ssscript.taczfixes.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ssscript.taczfixes.client.handler.SteplessZoomHandler;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.client.util.SwitchedDisplayManager;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.config.client.ZoomConfig;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerZoomSensitivity {

    @WrapOperation(method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void taczfixes$zoomSensitivity(LocalPlayer player, double yaw, double pitch,
                                           Operation<Void> original) {
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            original.call(player, yaw, pitch);
            return;
        }
        float stdZoom = standardScopeZoom(iGun, gun);
        float corrZoom = correctedZoom(iGun, gun);
        double ratio = sensitivityRatio(player, stdZoom, corrZoom);
        if (ratio == 1.0d) {
            original.call(player, yaw, pitch);
            return;
        }
        original.call(player, yaw * ratio, pitch * ratio);
    }

    private static float standardScopeZoom(IGun iGun, ItemStack gun) {
        ResourceLocation scopeId = iGun.getAttachmentId(gun, AttachmentType.SCOPE);
        if (scopeId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            scopeId = iGun.getBuiltInAttachmentId(gun, AttachmentType.SCOPE);
        }
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            return TimelessAPI.getGunDisplay(gun).map(GunDisplayInstance::getIronZoom).orElse(1f);
        }
        Optional<ClientAttachmentIndex> optional = SwitchedDisplayManager.getClientAttachmentIndex(gun, scopeId);
        if (!optional.isPresent()) {
            optional = TimelessAPI.getClientAttachmentIndex(scopeId);
        }
        if (optional.isPresent()) {
            float[] zoom = optional.get().getZoom();
            if (zoom != null && zoom.length > 0) {
                CompoundTag attachmentTag = iGun.getAttachmentTag(gun, AttachmentType.SCOPE);
                return zoom[AttachmentItemDataAccessor.getZoomNumberFromTag(attachmentTag) % zoom.length];
            }
        }
        return 1f;
    }

    private static float correctedZoom(IGun iGun, ItemStack gun) {
        float stepless = SteplessZoomHandler.getSteplessZoom(gun);
        if (stepless > 0.0f) {
            return stepless;
        }
        return ScopeSwitchState.aimingZoom(iGun, gun);
    }

    private static double sensitivityRatio(LocalPlayer player, float stdZoom, float corrZoom) {
        if (stdZoom <= 0f || corrZoom <= 0f) return 1.0d;
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (operator == null) return 1.0d;
        Minecraft minecraft = Minecraft.getInstance();
        float progress = operator.getSynAimingProgress();
        double originalFov = minecraft.options.fov().get();
        double coefficient = ZoomConfig.SCREEN_DISTANCE_COEFFICIENT.get();
        double stdFov = MathUtil.magnificationToFov(1 + (stdZoom - 1) * progress, originalFov);
        double corrFov = MathUtil.magnificationToFov(1 + (corrZoom - 1) * progress, originalFov);
        double stdRatio = MathUtil.zoomSensitivityRatio(stdFov, originalFov, coefficient);
        if (stdRatio <= 0.0d) return 1.0d;
        double corrRatio = MathUtil.zoomSensitivityRatio(corrFov, originalFov, coefficient);
        return corrRatio / stdRatio;
    }
}