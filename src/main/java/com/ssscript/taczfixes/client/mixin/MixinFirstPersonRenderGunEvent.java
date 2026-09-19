package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.DualFocusAimState;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import com.ssscript.taczfixes.client.util.CustomScopeViewShift;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.client.event.FirstPersonRenderGunEvent", remap = false)
public class MixinFirstPersonRenderGunEvent {
    @Inject(method = "applyFirstPersonPositioningTransform", at = @At("HEAD"), remap = false)
    private static void taczfixes$captureAimProgress(PoseStack poseStack, BedrockGunModel model, ItemStack stack, float aimingProgress, float refitScreenOpeningProgress, CallbackInfo ci) {
        ScopeSwitchState.aimingProgressValue = aimingProgress;
    }

    /** 自定义槽瞄具开镜偏移: 整枪定位完成后压入, 由 GunItemRendererWrapper.cacheMuzzlePosition 弹出,
     *  使枪口粒子绑定/模型渲染/枪口位置缓存都包含同一偏移。 */
    @Inject(method = "applyFirstPersonGunTransform", at = @At("RETURN"), remap = false)
    private static void taczfixes$pushCustomScopeViewShift(LocalPlayer player, ItemStack stack, PoseStack poseStack,
                                                           BedrockGunModel model, float partialTick, CallbackInfo ci) {
        if (model == null) return;
        CustomScopeViewShift.apply(poseStack, model, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
    }



    @Inject(method = {"onGunFire"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;", shift = At.Shift.BEFORE, remap = true)}, cancellable = true, remap = false)
    private static void dualWield$skipMainHandMuzzleForOffhandFire(GunFireEvent event, CallbackInfo callback) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getShooter() != player || !DualWieldClient.isDualMode(player)) {
            return;
        }
        ItemStack firedStack = event.getGunItemStack();
        if (firedStack != null && firedStack == player.getOffhandItem()) {
            callback.cancel();
        }
    }
}