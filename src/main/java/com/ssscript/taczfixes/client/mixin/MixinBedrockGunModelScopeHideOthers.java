package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.client.util.LensDepthWriter;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
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
        List<Object[]> standby = com.ssscript.taczfixes.client.util.StandbySlotBuffer.takePending();
        boolean aiming = taczfixes$isAimingScopeView(gun) && taczfixes$hasScopeViewAttachment(gun);
        if (accelerated) {
            // 加速路径: 渲染提交到 -940 层, 该层 before 函数与枪体共用镜头剔除模板测试
            for (Object[] slot : standby) {
                renderStandbySlot((ItemStack) slot[0], gun,
                        (BedrockPart) slot[1],
                        pose, displayContext, light, overlay);
            }
            return;
        }
        renderActiveSlotLast(pose, displayContext, light, overlay);
        // 与枪体保持一致: 应用 TACZ 原版镜头剔除模板函数(renderActiveSlotLast 渲染瞄具后已禁用模板测试)
        taczfixes$applyActiveScopeStencil(gun);
        for (Object[] slot : standby) {
            renderStandbySlot((ItemStack) slot[0], gun,
                    (BedrockPart) slot[1],
                    pose, displayContext, light, overlay);
        }
        if (!aiming) {
            return;
        }
        // 瞄准时: 配件仅在镜头内不可见, 镜头外正常渲染
        renderActiveSlotLast(pose, displayContext, light, overlay);
        if (com.ssscript.taczfixes.client.util.ScopeSwitchState.getActiveSlot(gun) == null) {
            taczfixes$rerenderStandardScope(pose, displayContext, light, overlay);
        }
        taczfixes$applyActiveScopeStencil(gun);
    }

    @Unique
    private void renderStandbySlot(ItemStack item, ItemStack gun, BedrockPart node,
                                   PoseStack pose, ItemDisplayContext displayContext,
                                   int light, int overlay) {
        if (isScopeAttachment(item)) {
            // scope 类型配件: 原始网格渲染, 不触发 renderScope/renderBoth 的模板清除与镜片绘制
            // (其自绘镜片会毁掉活动瞄具的镜头剔除模板, 导致镜内可见)
            com.ssscript.taczfixes.client.util.StandbySlotBuffer.renderRawMesh(
                    item, gun, node, pose, displayContext, light, overlay);
        } else {
            com.ssscript.taczfixes.client.util.StandbySlotBuffer.renderSlotAttachment(
                    item, gun, node, pose, displayContext, light, overlay);
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
        AttachmentRender.renderAttachment(scope, gun, pose, displayContext, light, overlay);
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
        com.ssscript.taczfixes.client.util.StandbySlotBuffer.applyNodePathTransform(actNode, pose);
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
            if (actItem != null && !actItem.isEmpty() && isScopeAttachment(actItem)) {
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
                com.ssscript.taczfixes.common.util.StencilStandbyState.set(516, 127, 255);
            } else if (index.isScope()) {
                RenderSystem.stencilFunc(514, 0, 255);
                com.ssscript.taczfixes.common.util.StencilStandbyState.set(514, 0, 255);
            } else {
                RenderHelper.disableItemEntityStencilTest();
                com.ssscript.taczfixes.common.util.StencilStandbyState.end();
            }
        });
    }
}