package com.ssscript.taczfixes.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.util.CustomSlotStorage;
import com.ssscript.taczfixes.util.LensDepthWriter;
import com.ssscript.taczfixes.util.ScopeSwitchState;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.AttachmentRender;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BedrockGunModel.class)
public abstract class MixinBedrockGunModelScopeHideOthers {

    @Shadow(remap = false) private ItemStack currentGunItem;
    @Shadow(remap = false) protected List<BedrockPart> scopePosPath;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/client/model/BedrockAnimatedModel;render" +
                    "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            remap = false), remap = false)
    private void taczfixes$stencilForActiveScope(PoseStack pose, ItemStack itemStack,
                                                 ItemDisplayContext displayContext, RenderType renderType,
                                                 int light, int overlay, CallbackInfo ci) {
        renderStandbyMasked(pose, displayContext, light, overlay, false);
    }

    @Inject(method = "renderAccelerated", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/client/model/BedrockAnimatedModel;render" +
                    "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            remap = false), remap = false)
    private void taczfixes$stencilForActiveScopeAccelerated(PoseStack pose, ItemStack itemStack,
                                                            ItemDisplayContext displayContext, RenderType renderType,
                                                            int light, int overlay, CallbackInfo ci) {
        renderStandbyMasked(pose, displayContext, light, overlay, true);
    }

    @Unique
    private void renderStandbyMasked(PoseStack pose, ItemDisplayContext displayContext,
                                     int light, int overlay, boolean accelerated) {
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        List<Object[]> standby = com.ssscript.taczfixes.util.StandbySlotBuffer.takePending();
        if (accelerated) {
            for (Object[] slot : standby) {
                com.ssscript.taczfixes.util.StandbySlotBuffer.renderSlotAttachment(
                        (ItemStack) slot[0], gun,
                        (BedrockPart) slot[1],
                        pose, displayContext, light, overlay);
            }
            return;
        }
        renderActiveSlotLast(pose, displayContext, light, overlay);
        boolean aiming = taczfixes$isAimingScopeView(gun) && taczfixes$hasScopeViewAttachment(gun);
        if (!aiming) {
            for (Object[] slot : standby) {
                com.ssscript.taczfixes.util.StandbySlotBuffer.renderSlotAttachment(
                        (ItemStack) slot[0], gun,
                        (BedrockPart) slot[1],
                        pose, displayContext, light, overlay);
            }
            return;
        }
        RenderHelper.disableItemEntityStencilTest();
        RenderSystem.stencilFunc(519, 0, 255);
        RenderSystem.stencilOp(7680, 7680, 7680);
        taczfixes$applyActiveScopeStencil(gun);
        com.ssscript.taczfixes.util.StencilStandbyState.begin();
        try {
            for (Object[] slot : standby) {
                RenderSystem.stencilMask(0);
                com.ssscript.taczfixes.util.StandbySlotBuffer.renderSlotAttachment(
                        (ItemStack) slot[0], gun,
                        (BedrockPart) slot[1],
                        pose, displayContext, light, overlay);
            }
        } finally {
            com.ssscript.taczfixes.util.StencilStandbyState.end();
        }
        RenderSystem.stencilMask(255);
        taczfixes$applyActiveScopeStencil(gun);
    }

    @Unique
    private void renderActiveSlotLast(PoseStack pose, ItemDisplayContext displayContext,
                                      int light, int overlay) {
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        String active = ScopeSwitchState.getActiveSlot(gun);
        if (active == null || active.isEmpty()) return;
        ItemStack actItem = CustomSlotStorage.get(gun, active);
        if (actItem == null || actItem.isEmpty()) return;
        BedrockAnimatedModel self = (BedrockAnimatedModel) (Object) this;
        BedrockPart actNode = self.getNode(active + "_pos");
        if (actNode == null) return;
        pose.pushPose();
        com.ssscript.taczfixes.util.StandbySlotBuffer.applyNodePathTransform(actNode, pose);
        AttachmentRender.renderAttachment(actItem, gun, pose, displayContext, light, overlay);
        if (isScopeAttachment(actItem)) {
            LensDepthWriter.writeLensDepth(actItem, gun, pose, displayContext, light, overlay);
        }
        pose.popPose();
    }

    @Unique
    private static boolean taczfixes$hasScopeViewAttachment(ItemStack gun) {
        String active = ScopeSwitchState.getActiveSlot(gun);
        if (active != null) {
            ItemStack actItem = CustomSlotStorage.get(gun, active);
            if (isScopeAttachment(actItem)) return true;
        }
        ItemStack standardScope = readStandardScope(gun);
        if (standardScope.isEmpty()) {
            IGun igun = IGun.getIGunOrNull(gun);
            if (igun != null) {
                standardScope = igun.getBuiltinAttachment(gun, AttachmentType.SCOPE);
            }
        }
        return isScopeAttachment(standardScope);
    }

    @Unique
    private static boolean isScopeAttachment(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return false;
        return TimelessAPI.getClientAttachmentIndex(ia.getAttachmentId(item))
                .map(ClientAttachmentIndex::isScope).orElse(false);
    }

    @Unique
    private static ItemStack readStandardScope(ItemStack gun) {
        net.minecraft.nbt.CompoundTag tag = gun.getTag();
        if (tag != null) {
            String key = com.tacz.guns.api.item.nbt.GunItemDataAccessor.GUN_ATTACHMENT_BASE + AttachmentType.SCOPE.name();
            if (tag.contains(key, 10)) {
                ItemStack scope = ItemStack.of(tag.getCompound(key));
                if (!scope.isEmpty()) return scope;
            }
        }
        return ItemStack.EMPTY;
    }

    @Unique
    private static boolean taczfixes$isAimingScopeView(ItemStack gun) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getCameraEntity() instanceof LocalPlayer player) || !player.isAlive()) return false;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null) return false;
        return operator.getClientAimingProgress(mc.getFrameTime()) > 0.001f;
    }

    @Unique
    private static void taczfixes$applyActiveScopeStencil(ItemStack gun) {
        String active = ScopeSwitchState.getActiveSlot(gun);
        if (active != null) {
            ItemStack actItem = CustomSlotStorage.get(gun, active);
            if (actItem != null && !actItem.isEmpty()) {
                if (!isScopeAttachment(actItem)) return;
                taczfixes$applyScopeStencilFunc(actItem);
                return;
            }
        }
        ItemStack lens = readStandardScope(gun);
        if (lens.isEmpty()) {
            IGun igun = IGun.getIGunOrNull(gun);
            if (igun != null) {
                lens = igun.getBuiltinAttachment(gun, AttachmentType.SCOPE);
            }
        }
        if (!isScopeAttachment(lens)) return;
        taczfixes$applyScopeStencilFunc(lens);
    }

    @Unique
    private static void taczfixes$applyScopeStencilFunc(ItemStack lens) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(lens);
        if (attachment == null) return;
        TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(lens)).ifPresent(index -> {
            RenderHelper.enableItemEntityStencilTest();
            if (index.isScope() && index.isSight()) {
                RenderSystem.stencilFunc(516, 127, 255);
                com.ssscript.taczfixes.util.StencilStandbyState.set(516, 127, 255);
            } else if (index.isScope()) {
                RenderSystem.stencilFunc(514, 0, 255);
                com.ssscript.taczfixes.util.StencilStandbyState.set(514, 0, 255);
            } else {
                RenderHelper.disableItemEntityStencilTest();
                com.ssscript.taczfixes.util.StencilStandbyState.end();
            }
        });
    }
}