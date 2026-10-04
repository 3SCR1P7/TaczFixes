package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.client.util.CustomSlotMount;
import com.ssscript.taczfixes.client.util.CustomSlotRenderBridge;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.client.util.StandbySlotBuffer;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.AttachmentRender;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.List;

@Mixin(BedrockGunModel.class)
public abstract class MixinBedrockGunModelScopeHideOthers implements CustomSlotRenderBridge {

    @Shadow(remap = false) private ItemStack currentGunItem;
    @Shadow(remap = false) protected List<BedrockPart> scopePosPath;

    private static final String TACZFIXES_RENDER_DESC =
            "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V";
    private static final String TACZFIXES_ACCEL_DESC =
            "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IILnet/minecraft/client/renderer/MultiBufferSource;)V";
    private static final String TACZFIXES_SUPER_RENDER_TARGET =
            "Lcom/tacz/guns/client/model/BedrockAnimatedModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V";

    @Unique
    private boolean taczfixes$standbyRendered;

    @Inject(method = "render" + TACZFIXES_RENDER_DESC, at = @At("HEAD"), remap = false)
    private void taczfixes$resetStandbyRendered(PoseStack pose, ItemStack itemStack,
                                                ItemDisplayContext displayContext, RenderType renderType,
                                                int light, int overlay, float red, float green, float blue, float alpha,
                                                net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                                CallbackInfo ci) {
        taczfixes$standbyRendered = false;
    }

    @Inject(method = "render" + TACZFIXES_RENDER_DESC, at = @At(value = "INVOKE",
            target = TACZFIXES_SUPER_RENDER_TARGET, remap = false), remap = false)
    private void taczfixes$renderStandbySlots(PoseStack pose, ItemStack itemStack,
                                              ItemDisplayContext displayContext, RenderType renderType,
                                              int light, int overlay, float red, float green, float blue, float alpha,
                                              net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                              CallbackInfo ci) {
        renderStandbyIfConfigured(pose, displayContext, light, overlay);
    }

    /**
     * 兜底: 若本次 `BedrockGunModel.render` 既没有走加速分支、也没有在 super.render 处渲染自定义槽
     * (例如第三人称某些路径), 在 RETURN 时补渲染一次。已渲染过则跳过, 避免重复。
     */
    @Inject(method = "render" + TACZFIXES_RENDER_DESC, at = @At("RETURN"), remap = false)
    private void taczfixes$ensureStandbyRendered(PoseStack pose, ItemStack itemStack,
                                                 ItemDisplayContext displayContext, RenderType renderType,
                                                 int light, int overlay, float red, float green, float blue, float alpha,
                                                 net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                                 CallbackInfo ci) {
        if (taczfixes$standbyRendered) {
            return;
        }
        StandbySlotBuffer.takePending();
        taczfixes$renderCustomSlots(pose, displayContext, light, overlay);
    }

    /** 外部渲染路径(第三人称 SBM 外部网格)用: 不经过 BedrockGunModel.render, 直接补画自定义槽配件。 */
    @Override
    public void taczfixes$renderCustomSlotsFor(ItemStack gun, PoseStack pose, ItemDisplayContext displayContext,
                                               int light, int overlay) {
        ItemStack previous = this.currentGunItem;
        this.currentGunItem = gun;
        try {
            taczfixes$renderCustomSlots(pose, displayContext, light, overlay);
        } finally {
            this.currentGunItem = previous;
        }
    }

    @Unique
    private void taczfixes$renderCustomSlots(PoseStack pose, ItemDisplayContext displayContext,
                                             int light, int overlay) {
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) return;
        java.util.Map<String, CustomSlotManager.SlotEntry> slots = CustomSlotManager.getEntries(gun);
        if (slots.isEmpty()) return;
        BedrockAnimatedModel self = (BedrockAnimatedModel) (Object) this;
        String active = ScopeSwitchState.getActiveSlot(gun);
        renderActiveSlot(pose, displayContext, light, overlay);
        for (java.util.Map.Entry<String, CustomSlotManager.SlotEntry> entry : slots.entrySet()) {
            String slotId = entry.getKey();
            if (slotId.equals(active)) continue;
            ItemStack item = CustomSlotStorage.get(gun, slotId);
            if (item.isEmpty()) continue;
            BedrockPart node = CustomSlotMount.nodeFor(self, entry.getValue(), slotId);
            if (node == null) continue;
            renderStandbySlot(item, gun, node, slotId, !entry.getValue().source().isEmpty(),
                    CustomSlotMount.sourceChain(self, gun, entry.getValue()),
                    pose, displayContext, light, overlay);
        }
    }

    @Inject(method = "renderAccelerated" + TACZFIXES_ACCEL_DESC, at = @At("HEAD"), remap = false)
    private void taczfixes$renderStandbySlotsAccelerated(PoseStack pose, ItemStack itemStack,
                                                         ItemDisplayContext displayContext, RenderType renderType,
                                                         int light, int overlay,
                                                         net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                                         CallbackInfo ci) {
        // Submit custom attachments before TaCZ pushes the gun-body callbacks.
        // Otherwise their layers inherit the body's after callback, which clears
        // stencil before the remaining scope layers consume it.
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null || CustomSlotManager.getSlots(gun).isEmpty()) return;
        taczfixes$standbyRendered = true;
        renderActiveSlot(pose, displayContext, light, overlay);
        for (Object[] slot : StandbySlotBuffer.takePending()) {
            renderStandbySlot((ItemStack) slot[0], gun, (BedrockPart) slot[1],
                    (String) slot[2], taczfixes$isAttachmentNode(slot), taczfixes$chain(slot),
                    pose, displayContext, light, overlay);
        }
        // Native AR scope callbacks establish stencil when the GPU actually draws.
        // Do not run the immediate stencil repair or duplicate the standard scope.
    }

    @Unique
    private void renderStandbyIfConfigured(PoseStack pose, ItemDisplayContext displayContext,
                                           int light, int overlay) {
        // Ordinary guns need no custom-slot stencil repair or extra ocular pass.
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null || CustomSlotManager.getSlots(gun).isEmpty()) return;
        taczfixes$standbyRendered = true;
        renderStandby(pose, displayContext, light, overlay);
    }

    @Unique
    private void renderStandby(PoseStack pose, ItemDisplayContext displayContext,
                               int light, int overlay) {
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;

        renderActiveSlot(pose, displayContext, light, overlay);

        taczfixes$applyActiveScopeStencil(gun);
        com.mojang.blaze3d.systems.RenderSystem.stencilOp(org.lwjgl.opengl.GL11.GL_KEEP,
                org.lwjgl.opengl.GL11.GL_KEEP, org.lwjgl.opengl.GL11.GL_KEEP);

        List<Object[]> standby = StandbySlotBuffer.takePending();
        for (Object[] slot : standby) {
            renderStandbySlot((ItemStack) slot[0], gun,
                    (BedrockPart) slot[1],
                    (String) slot[2],
                    taczfixes$isAttachmentNode(slot),
                    taczfixes$chain(slot),
                    pose, displayContext, light, overlay);
        }

        String activeSlot = ScopeSwitchState.getActiveSlot(gun);
        if (activeSlot != null && !activeSlot.isEmpty()) {
            renderActiveSlot(pose, displayContext, light, overlay);
        } else {
            rerenderStandardScope(pose, displayContext, light, overlay);
        }

        taczfixes$applyActiveScopeStencil(gun);
        com.mojang.blaze3d.systems.RenderSystem.stencilOp(org.lwjgl.opengl.GL11.GL_KEEP,
                org.lwjgl.opengl.GL11.GL_KEEP, org.lwjgl.opengl.GL11.GL_KEEP);
    }


    @Unique
    private void rerenderStandardScope(PoseStack pose, ItemDisplayContext displayContext,
                                       int light, int overlay) {
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        if (this.scopePosPath == null) return;
        ItemStack scope = readStandardScope(gun);
        if (scope.isEmpty()) {
            IGun igun = IGun.getIGunOrNull(gun);
            if (igun != null) {
                scope = igun.getBuiltinAttachment(gun, AttachmentType.SCOPE);
            }
        }
        if (scope.isEmpty()) return;
        pose.pushPose();
        for (BedrockPart part : this.scopePosPath) {
            part.translateAndRotateAndScale(pose);
        }
        AttachmentRender.renderAttachment(
                scope, gun, AttachmentType.SCOPE,
                pose, displayContext, light, overlay);
        pose.popPose();
    }

    @Unique
    private void renderActiveSlot(PoseStack pose, ItemDisplayContext displayContext,
                                  int light, int overlay) {
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        String active = ScopeSwitchState.getActiveSlot(gun);
        if (active == null || active.isEmpty()) return;
        ItemStack actItem = CustomSlotStorage.get(gun, active);
        if (actItem == null || actItem.isEmpty()) return;
        CustomSlotManager.SlotEntry activeEntry = CustomSlotManager.getEntry(gun, active);
        if (activeEntry == null) return;
        BedrockAnimatedModel self = (BedrockAnimatedModel) (Object) this;
        BedrockPart actNode = CustomSlotMount.nodeFor(self, activeEntry, active);
        if (actNode == null) return;
        pose.pushPose();
        CustomSlotMount.apply(pose, CustomSlotMount.sourceChain(self, gun, activeEntry), gun,
                displayContext, light, overlay);
        if (!activeEntry.source().isEmpty()) {
            pose.translate(0.0F, -1.5F, 0.0F);
        }
        StandbySlotBuffer.applyNodePathTransform(actNode, pose);
        StandbySlotBuffer.applyPosAlter(actNode, gun, pose, active);
        pose.translate(0.0F, -1.5F, 0.0F);
        StandbySlotBuffer.applySlotAdapterOffset(actItem, gun, active, pose, displayContext, light, overlay);
        IAttachment ia = IAttachment.getIAttachmentOrNull(actItem);
        if (ia != null) {
            ResourceLocation actId = ia.getAttachmentId(actItem);
            com.ssscript.taczfixes.client.util.GunRecolorManager.pushRenderStack(actItem);
            try {
                TimelessAPI.getClientAttachmentIndex(actId).ifPresent(index -> {
                    com.tacz.guns.client.model.BedrockAttachmentModel actModel = index.getAttachmentModel();
                    ResourceLocation actTexture = index.getModelTexture();
                    if (actModel != null && actTexture != null) {
                        RenderType renderType = RenderType.entityCutout(actTexture);
                        actModel.render(actItem, gun, pose, displayContext, renderType, light, overlay);
                    }
                });
            } finally {
                com.ssscript.taczfixes.client.util.GunRecolorManager.popRenderStack();
            }
        }
        pose.popPose();
    }

    @Unique
    private static void taczfixes$applyActiveScopeStencil(ItemStack gun) {
        String active = ScopeSwitchState.getActiveSlot(gun);
        if (active != null) {
            ItemStack actItem = CustomSlotStorage.get(gun, active);
            if (actItem != null && !actItem.isEmpty() && isScopeLikeAttachment(actItem)) {
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
        if (isScopeLikeAttachment(lens)) {
            taczfixes$applyScopeStencilFunc(lens);
        }
    }

    @Unique
    private static void taczfixes$applyScopeStencilFunc(ItemStack lens) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(lens);
        if (attachment == null) return;
        TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(lens)).ifPresent(index -> {
            if (index.isScope() && index.isSight()) {


                com.tacz.guns.util.RenderHelper.enableItemEntityStencilTest();
                com.mojang.blaze3d.systems.RenderSystem.stencilFunc(org.lwjgl.opengl.GL11.GL_GREATER, 127, 255);
            } else if (index.isScope()) {
                // 绾瓛鐘堕暅: 闀滅墖澶?EQUAL 0)鍓旈櫎

                com.tacz.guns.util.RenderHelper.enableItemEntityStencilTest();
                com.mojang.blaze3d.systems.RenderSystem.stencilFunc(org.lwjgl.opengl.GL11.GL_EQUAL, 0, 255);
            }

        });
    }


    @Unique
    @SuppressWarnings("unchecked")
    private static java.util.List<CustomSlotMount.Step> taczfixes$chain(Object[] slot) {
        if (slot.length > 3 && slot[3] instanceof java.util.List<?> list) {
            return (java.util.List<CustomSlotMount.Step>) list;
        }
        return java.util.List.of();
    }

    @Unique
    private static boolean taczfixes$isAttachmentNode(Object[] slot) {
        return slot.length > 4 && Boolean.TRUE.equals(slot[4]);
    }

    @Unique
    private void renderStandbySlot(ItemStack item, ItemStack gun, BedrockPart node,
                                   String slotId, boolean attachmentNode, java.util.List<CustomSlotMount.Step> chain,
                                   PoseStack pose, ItemDisplayContext displayContext,
                                   int light, int overlay) {
        if (node == null) return;
        if (!isScopeLikeAttachment(item)) {
            // 闈炵瀯鍏烽厤浠?laser/grip 绛?: 璧板師鐗?renderSlotAttachment 绠＄嚎(鍚?mount 澶勭悊), 浣嶇疆姝ｇ‘
            StandbySlotBuffer.renderSlotAttachment(item, gun, node, pose, displayContext, light, overlay, slotId, chain, attachmentNode);
            return;
        }
        pose.pushPose();
        CustomSlotMount.apply(pose, chain, gun, displayContext, light, overlay);
        if (attachmentNode) {
            pose.translate(0.0F, -1.5F, 0.0F);
        }
        StandbySlotBuffer.applyNodePathTransform(node, pose);
        StandbySlotBuffer.applyPosAlter(node, gun, pose, slotId);
        pose.translate(0.0F, -1.5F, 0.0F);
        StandbySlotBuffer.applySlotAdapterOffset(item, gun, slotId, pose, displayContext, light, overlay);


        renderLikeNative(item, gun, pose, displayContext, light, overlay);
        pose.popPose();
    }




    @Unique
    private static void renderLikeNative(ItemStack item, ItemStack gun, PoseStack pose,
                                         ItemDisplayContext displayContext, int light, int overlay) {
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return;
        ResourceLocation id = ia.getAttachmentId(item);
        if (id == null) return;
        java.util.Optional<com.tacz.guns.client.resource.index.ClientAttachmentIndex> idx =
                TimelessAPI.getClientAttachmentIndex(id);
        if (!idx.isPresent()) return;
        com.ssscript.taczfixes.client.util.GunRecolorManager.pushRenderStack(item);
        try {
            com.tacz.guns.client.model.BedrockAttachmentModel model = idx.get().getAttachmentModel();
            ResourceLocation texture = idx.get().getModelTexture();
            if (model == null || texture == null) return;
            java.util.Map<com.tacz.guns.client.model.bedrock.BedrockPart, Boolean> restore = new java.util.LinkedHashMap<>();

            StandbySlotBuffer.ensureScopeOcularVisible(model, restore);
            MixinBedrockAttachmentModelScopeSuppress acc = (MixinBedrockAttachmentModelScopeSuppress) model;

            try {
                taczfixes$activateIfPresent(restore, acc.taczfixes$scopeBodyPath());
                taczfixes$activateIfPresent(restore, acc.taczfixes$ocularRingPath());
                java.util.List<java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart>> oculars = acc.taczfixes$ocularNodePaths();
                if (oculars != null) {
                    java.util.List<Boolean> ocularFlags = acc.taczfixes$isScopeOcular();
                    for (int i = 0; i < oculars.size(); i++) {
                        boolean isScopeOcular = ocularFlags != null && i < ocularFlags.size()
                                && Boolean.TRUE.equals(ocularFlags.get(i));
                        if (isScopeOcular) {
                            taczfixes$activateIfPresent(restore, oculars.get(i));
                        } else {
                            taczfixes$hideIfPresent(restore, oculars.get(i));
                        }
                    }
                }
                java.util.List<java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart>> divisions = acc.taczfixes$divisionNodePaths();
                if (divisions != null) {
                    for (java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> path : divisions) {
                        taczfixes$hideIfPresent(restore, path);
                    }
                }
                boolean oldScope = acc.taczfixes$isScope();
                boolean oldSight = acc.taczfixes$isSight();
                acc.taczfixes$setIsScope(false);
                acc.taczfixes$setIsSight(false);

                try {
                    model.render(item, gun, pose, displayContext,
                            RenderType.entityCutout(texture), light, overlay);
                } finally {
                    acc.taczfixes$setIsScope(oldScope);
                    acc.taczfixes$setIsSight(oldSight);
                }
            } finally {
                StandbySlotBuffer.restoreOcularVisibility(restore);
            }
        } finally {
            com.ssscript.taczfixes.client.util.GunRecolorManager.popRenderStack();
        }
    }

    @Unique
    private static void taczfixes$activateIfPresent(java.util.Map<com.tacz.guns.client.model.bedrock.BedrockPart, Boolean> restore,
                                                    java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> path) {
        if (path == null || path.isEmpty()) return;
        com.tacz.guns.client.model.bedrock.BedrockPart part = path.get(path.size() - 1);
        if (!part.visible) {
            restore.putIfAbsent(part, part.visible);
            part.visible = true;
        }
    }

    @Unique
    private static void taczfixes$hideIfPresent(java.util.Map<com.tacz.guns.client.model.bedrock.BedrockPart, Boolean> restore,
                                                java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> path) {
        if (path == null || path.isEmpty()) return;
        com.tacz.guns.client.model.bedrock.BedrockPart part = path.get(path.size() - 1);
        if (part.visible) {
            restore.putIfAbsent(part, part.visible);
            part.visible = false;
        }
    }

    @Unique
    private static boolean isScopeLikeAttachment(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return false;
        return TimelessAPI.getClientAttachmentIndex(ia.getAttachmentId(item))
                .map(index -> index.isScope() || index.isSight()).orElse(false);
    }



    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "scopeHiddenRender",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/EnumMap;get(Ljava/lang/Object;)Ljava/lang/Object;", remap = false),
            remap = false)
    private Object taczfixes$realStandardScope(EnumMap<AttachmentType, ItemStack> map, Object key,
                                               com.llamalad7.mixinextras.injector.wrapoperation.Operation<Object> original) {
        if (key == AttachmentType.SCOPE) {
            ItemStack real = readStandardScope(this.currentGunItem);
            if (real != null && !real.isEmpty()) {
                return real;
            }
        }
        return original.call(map, key);
    }

    @Unique
    private static ItemStack readStandardScope(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        if (tag != null) {
            String key = com.tacz.guns.api.item.nbt.GunItemDataAccessor.GUN_ATTACHMENT_BASE + AttachmentType.SCOPE.name();
            if (tag.contains(key, 10)) {
                ItemStack scope = ItemStack.of(tag.getCompound(key));
                if (!scope.isEmpty()) return scope;
            }
        }
        return ItemStack.EMPTY;
    }
}
