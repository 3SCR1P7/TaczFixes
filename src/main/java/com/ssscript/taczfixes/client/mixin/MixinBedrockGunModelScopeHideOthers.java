package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.client.util.LensDepthWriter;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.client.util.StandbySlotBuffer;
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
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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

import java.util.List;

@Mixin(BedrockGunModel.class)
public abstract class MixinBedrockGunModelScopeHideOthers {

    @Shadow(remap = false) private ItemStack currentGunItem;
    @Shadow(remap = false) protected List<BedrockPart> scopePosPath;

    private static final String TACZFIXES_RENDER_DESC =
            "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";
    private static final String TACZFIXES_ACCEL_DESC =
            "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IILnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";
    private static final String TACZFIXES_SUPER_RENDER_TARGET =
            "Lcom/tacz/guns/client/model/BedrockAnimatedModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";
    private static final String TACZFIXES_ACCEL_SUPER_TARGET =
            "Lcom/tacz/guns/client/model/BedrockAnimatedModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IILnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";

    @Inject(method = "render" + TACZFIXES_RENDER_DESC, at = @At(value = "INVOKE",
            target = TACZFIXES_SUPER_RENDER_TARGET, remap = false), remap = false)
    private void taczfixes$stencilForActiveScope(PoseStack pose, ItemStack itemStack,
                                                 ItemDisplayContext displayContext, RenderType renderType,
                                                 int light, int overlay, float red, float green, float blue, float alpha,
                                                 net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource,
                                                 CallbackInfo ci) {
        renderStandbyMasked(pose, displayContext, light, overlay, false);
    }

    @Inject(method = "renderAccelerated" + TACZFIXES_ACCEL_DESC, at = @At(value = "INVOKE",
            target = TACZFIXES_SUPER_RENDER_TARGET, remap = false), remap = false)
    private void taczfixes$stencilForActiveScopeAccelerated(PoseStack pose, ItemStack itemStack,
                                                            ItemDisplayContext displayContext, RenderType renderType,
                                                            int light, int overlay,
                                                            net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource,
                                                            CallbackInfo ci) {
        renderStandbyMasked(pose, displayContext, light, overlay, true);
    }

    @Unique
    private void renderStandbyMasked(PoseStack pose, ItemDisplayContext displayContext,
                                     int light, int overlay, boolean accelerated) {
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        List<Object[]> standby = StandbySlotBuffer.takePending();
        boolean aiming = taczfixes$isAimingScopeView(gun) && taczfixes$hasScopeViewAttachment(gun);
        if (accelerated) {
            for (Object[] slot : standby) {
                renderStandbySlot((ItemStack) slot[0], gun,
                        (BedrockPart) slot[1],
                        (String) slot[2],
                        pose, displayContext, light, overlay, aiming);
            }
            return;
        }
        renderActiveSlotLast(pose, displayContext, light, overlay);
        taczfixes$applyActiveScopeStencil(gun);
        for (Object[] slot : standby) {
            // 备用镜帧: 抑制火控预测框(仅瞄准场景的核心镜才绘制)
            com.ssscript.taczfixes.client.util.RangefinderDrawBudget.setAimingScene(false);
            renderStandbySlot((ItemStack) slot[0], gun,
                    (BedrockPart) slot[1],
                    (String) slot[2],
                    pose, displayContext, light, overlay, aiming);
            com.ssscript.taczfixes.client.util.RangefinderDrawBudget.setAimingScene(aiming);
        }
        if (!aiming) {
            return;
        }
        if (ScopeSwitchState.getActiveSlot(gun) == null) {
            taczfixes$rerenderStandardScope(pose, displayContext, light, overlay);
        }
        taczfixes$applyActiveScopeStencil(gun);
    }

    @Unique
    private void renderStandbySlot(ItemStack item, ItemStack gun, BedrockPart node,
                                   String slotId, PoseStack pose, ItemDisplayContext displayContext,
                                   int light, int overlay, boolean aiming) {
        if (isScopeLikeAttachment(item)) {
            // 镜类配件: 临时关闭模板测试, 避免被 active ocular 的 stencil 值(GL_EQUAL 0)剔除自身镜片玻璃;
            // 渲染后恢复, 保留后续枪体在镜筒外被裁剪的原版语义
            RenderHelper.disableItemEntityStencilTest();
            StandbySlotBuffer.renderRawMesh(item, gun, node, pose, displayContext, light, overlay, slotId);
            taczfixes$applyActiveScopeStencil(gun);
        } else if (aiming) {
            // 非镜配件(laser 等): 瞄准时保持模板测试开启, 使镜筒内(stencil 非 0)被正确剔除;
            // 但镜外正常渲染
            StandbySlotBuffer.renderSlotAttachment(item, gun, node, pose, displayContext, light, overlay, slotId);
            taczfixes$applyActiveScopeStencil(gun);
        } else {
            // 未瞄准: 无镜片语义, 关闭模板测试保证附件完整可见
            RenderHelper.disableItemEntityStencilTest();
            StandbySlotBuffer.renderSlotAttachment(item, gun, node, pose, displayContext, light, overlay, slotId);
            taczfixes$applyActiveScopeStencil(gun);
        }
    }

    @Unique
    private void taczfixes$rerenderStandardScope(PoseStack pose, ItemDisplayContext displayContext,
                                                 int light, int overlay) {
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        ItemStack scope = readStandardScope(gun);
        if (scope.isEmpty() || this.scopePosPath == null) return;
        pose.pushPose();
        for (BedrockPart part : this.scopePosPath) {
            part.translateAndRotateAndScale(pose);
        }
        AttachmentRender.renderAttachment(scope, gun, AttachmentType.SCOPE,
                pose, displayContext, light, overlay);
        pose.popPose();
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
        StandbySlotBuffer.applyNodePathTransform(actNode, pose);
        pose.translate(0.0F, -1.5F, 0.0F);
        // 按槽适配器(Model+mountOffset) 与配件同一层级(与原版 renderAttachment 布局一致)
        StandbySlotBuffer.applySlotAdapterOffset(actItem, gun, active, pose, displayContext, light, overlay);
        IAttachment ia = IAttachment.getIAttachmentOrNull(actItem);
        if (ia != null) {
            ResourceLocation actId = ia.getAttachmentId(actItem);
            TimelessAPI.getClientAttachmentIndex(actId).ifPresent(index -> {
                com.tacz.guns.client.model.BedrockAttachmentModel actModel = index.getAttachmentModel();
                ResourceLocation actTexture = index.getModelTexture();
                if (actModel != null && actTexture != null) {
                    RenderType renderType = RenderType.entityCutout(actTexture);
                    // 未瞄准时以普通 cube 渲染 ocular(与 standby 一致):
                    // 筒状可见/红点不可见, 渲染后恢复; 瞄准时走第一人称模板路径(原版语义)
                    java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> restore = new java.util.ArrayList<>();
                    boolean suppressed = !taczfixes$isAimingScopeView(gun);
                    if (suppressed) {
                        StandbySlotBuffer.ensureOcularVisibility(actModel, restore);
                    }
                    // 瞄准场景: 允许火控预测框绘制(核心镜)
                    com.ssscript.taczfixes.client.util.RangefinderDrawBudget.setAimingScene(!suppressed);
                    try {
                        actModel.render(actItem, gun, pose, displayContext, renderType, light, overlay);
                    } finally {
                        com.ssscript.taczfixes.client.util.RangefinderDrawBudget.setAimingScene(false);
                        StandbySlotBuffer.restoreOcularVisibility(restore);
                    }
                    // active 槽渲染可能走 renderScope/renderBoth: 内部 clearStencil + clear 会清掉模板,
                    // 未瞄准时无有效镜片语义, 这里先关闭模板并清空, 避免污染后续枪体 stencil 流程
                    if (!taczfixes$isAimingScopeView(gun)) {
                        RenderHelper.disableItemEntityStencilTest();
                    }
                }
            });
        }
        if (isScopeLikeAttachment(actItem) && taczfixes$isAimingScopeView(gun)) {
            LensDepthWriter.writeLensDepth(actItem, gun, pose, displayContext, light, overlay);
        }
        pose.popPose();
    }

    @Unique
    private static AttachmentType attachmentTypeOf(ItemStack item, AttachmentType fallback) {
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return fallback;
        AttachmentType type = ia.getType(item);
        return type == null || type == AttachmentType.NONE ? fallback : type;
    }

    @Unique
    private static boolean taczfixes$hasScopeViewAttachment(ItemStack gun) {
        String active = ScopeSwitchState.getActiveSlot(gun);
        if (active != null) {
            ItemStack actItem = CustomSlotStorage.get(gun, active);
            if (isScopeLikeAttachment(actItem)) return true;
        }
        ItemStack standardScope = readStandardScope(gun);
        if (standardScope.isEmpty()) {
            IGun igun = IGun.getIGunOrNull(gun);
            if (igun != null) {
                standardScope = igun.getBuiltinAttachment(gun, AttachmentType.SCOPE);
            }
        }
        return isScopeLikeAttachment(standardScope);
    }

    @Unique
    private static boolean isScopeLikeAttachment(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return false;
        return TimelessAPI.getClientAttachmentIndex(ia.getAttachmentId(item))
                .map(index -> index.isScope() || index.isSight()).orElse(false);
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
        if (!isScopeLikeAttachment(lens)) return;
        taczfixes$applyScopeStencilFunc(lens);
    }

    @Unique
    private static void taczfixes$applyScopeStencilFunc(ItemStack lens) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(lens);
        if (attachment == null) return;
        TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(lens)).ifPresent(index -> {
            RenderHelper.enableItemEntityStencilTest();
            if (index.isScope() && index.isSight()) {
                RenderSystem.stencilFunc(org.lwjgl.opengl.GL11.GL_GREATER, 127, 255);
                com.ssscript.taczfixes.common.util.StencilStandbyState.set(516, 127, 255);
            } else if (index.isScope()) {
                RenderSystem.stencilFunc(org.lwjgl.opengl.GL11.GL_EQUAL, 0, 255);
                com.ssscript.taczfixes.common.util.StencilStandbyState.set(514, 0, 255);
            } else {
                RenderHelper.disableItemEntityStencilTest();
                com.ssscript.taczfixes.common.util.StencilStandbyState.end();
            }
        });
    }
}
