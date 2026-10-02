package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.client.util.CustomScopeViewShift;
import com.ssscript.taczfixes.client.util.CustomSlotMount;
import com.ssscript.taczfixes.client.util.LensDepthWriter;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.compat.ar.ARCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(BedrockGunModel.class)
public abstract class MixinBedrockGunModelCustomSlot {

    @Shadow(remap = false) private EnumMap<AttachmentType, ItemStack> currentAttachmentItem;
    @Shadow(remap = false) private ItemStack currentGunItem;
    @Shadow(remap = false) private Set<String> adapterToRender;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/compat/ar/ARCompat;shouldAccelerate()Z", remap = false), remap = false)
    private void taczfixes$customSlotRender(PoseStack poseStack, ItemStack itemStack, ItemDisplayContext displayContext, RenderType renderType, int light, int overlay, float red, float green, float blue, float alpha, net.minecraft.client.renderer.MultiBufferSource bufferSource, CallbackInfo ci) {
        handleCustomSlots(poseStack, displayContext, light, overlay, bufferSource);


        boolean aiming = taczfixes$isAiming(itemStack);
        boolean customActive = ScopeSwitchState.getActiveSlot(itemStack) != null;
        if (aiming && !customActive) {
            com.ssscript.taczfixes.client.util.RangefinderDrawBudget.setAimingScene(true);
        }
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("RETURN"), remap = false)
    private void taczfixes$renderEnd(PoseStack poseStack, ItemStack itemStack, ItemDisplayContext displayContext, RenderType renderType, int light, int overlay, float red, float green, float blue, float alpha, net.minecraft.client.renderer.MultiBufferSource bufferSource, CallbackInfo ci) {
        com.ssscript.taczfixes.client.util.RangefinderDrawBudget.setAimingScene(false);
    }

    @Unique
    private static boolean taczfixes$isAiming(ItemStack gun) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getCameraEntity() instanceof LocalPlayer player) || !player.isAlive()) return false;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null) return false;
        float progress = ScopeSwitchState.aimingProgressValue;
        if (progress < 0.001f) {
            progress = operator.getClientAimingProgress(mc.getFrameTime());
        }
        return progress > 0.5f;
    }

    private void handleCustomSlots(PoseStack poseStack, ItemDisplayContext displayContext, int light, int overlay, net.minecraft.client.renderer.MultiBufferSource bufferSource) {
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) return;
        ResourceLocation gunId = igun.getGunId(gun);
        Map<String, CustomSlotManager.SlotEntry> slots = CustomSlotManager.getEntries(gun);
        if (slots.isEmpty()) return;

        if (this.currentAttachmentItem != null) {
            ItemStack laser = this.currentAttachmentItem.get(AttachmentType.LASER);
            if (CustomSlotStorage.isLaserFromCustomSlot(gun, laser)) {
                this.currentAttachmentItem.put(AttachmentType.LASER, ItemStack.EMPTY);
                IAttachment attachment = IAttachment.getIAttachmentOrNull(laser);
                if (attachment != null && this.adapterToRender != null) {
                    TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(laser))
                            .map(ClientAttachmentIndex::getAdapterNodeName)
                            .filter(name -> name != null && !name.isEmpty())
                            .ifPresent(name -> this.adapterToRender.add(name));
                }
            }
            ItemStack standardScope = CustomScopeViewShift.readStandardScope(gun);
            if (standardScope.isEmpty()) {
                standardScope = igun.getBuiltinAttachment(gun, AttachmentType.SCOPE);
            }
            this.currentAttachmentItem.put(AttachmentType.SCOPE, standardScope);
        }

        BedrockAnimatedModel self = (BedrockAnimatedModel) (Object) this;
        String active = ScopeSwitchState.getActiveSlot(gun);
        boolean accelerated = ARCompat.shouldAccelerate();

        if (active != null && slots.containsKey(active)) {
            ItemStack actItem = CustomSlotStorage.get(gun, active);
            CustomSlotManager.SlotEntry activeEntry = slots.get(active);
            if (accelerated) {
                if (this.currentAttachmentItem != null && !actItem.isEmpty()) {
                    this.currentAttachmentItem.put(AttachmentType.SCOPE, actItem);
                }
                com.tacz.guns.client.model.bedrock.BedrockPart actNode =
                        CustomSlotMount.nodeFor(self, activeEntry, active);
                if (actNode != null && CustomScopeViewShift.isScopeAttachment(actItem)) {
                    poseStack.pushPose();
                    CustomSlotMount.apply(poseStack, CustomSlotMount.sourceChain(self, gun, activeEntry),
                            gun, displayContext, light, overlay);
                    if (!activeEntry.source().isEmpty()) {
                        poseStack.translate(0.0F, -1.5F, 0.0F);
                    }
                    applyNodePathTransform(actNode, poseStack);
                    com.ssscript.taczfixes.client.util.StandbySlotBuffer.applyPosAlter(actNode, gun, poseStack, active);
                    LensDepthWriter.writeLensDepth(actItem, gun, poseStack, displayContext, light, overlay);
                    poseStack.popPose();
                }
            }
        }

        List<Object[]> standby = new ArrayList<>();
        for (Map.Entry<String, CustomSlotManager.SlotEntry> entry : slots.entrySet()) {
            String slotId = entry.getKey();
            if (slotId.equals(active)) continue;
            ItemStack item = CustomSlotStorage.get(gun, slotId);
            if (item.isEmpty()) continue;
            com.tacz.guns.client.model.bedrock.BedrockPart node =
                    CustomSlotMount.nodeFor(self, entry.getValue(), slotId);
            if (node == null) continue;
            standby.add(new Object[]{item, node, slotId,
                    CustomSlotMount.sourceChain(self, gun, entry.getValue()),
                    !entry.getValue().source().isEmpty()});
        }
        com.ssscript.taczfixes.client.util.StandbySlotBuffer.setPending(standby);
    }

    private static void applyNodePathTransform(com.tacz.guns.client.model.bedrock.BedrockPart node,
                                               PoseStack pose) {
        List<com.tacz.guns.client.model.bedrock.BedrockPart> path = new ArrayList<>();
        com.tacz.guns.client.model.bedrock.BedrockPart cur = node;
        while (cur != null) {
            path.add(cur);
            cur = cur.getParent();
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            path.get(i).translateAndRotateAndScale(pose);
        }
    }
}
