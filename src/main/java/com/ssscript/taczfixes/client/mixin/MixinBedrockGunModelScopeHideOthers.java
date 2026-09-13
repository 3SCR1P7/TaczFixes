package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * 自定义槽位瞄具渲染: 玩家未使用的瞄具(standby), 其 ocular 一律作为普通 cube 渲染,
 * 不触发任何 ocular 功能; 玩家当前使用中的瞄具(active)走 TACZ 原生功能路径。
 */
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
            "Lcom/tacz/guns/client/model/BedrockAnimatedModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";

    @Inject(method = "render" + TACZFIXES_RENDER_DESC, at = @At(value = "INVOKE",
            target = TACZFIXES_SUPER_RENDER_TARGET, remap = false), remap = false)
    private void taczfixes$renderStandbySlots(PoseStack pose, ItemStack itemStack,
                                              ItemDisplayContext displayContext, RenderType renderType,
                                              int light, int overlay, float red, float green, float blue, float alpha,
                                              net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource,
                                              CallbackInfo ci) {
        renderStandby(pose, displayContext, light, overlay);
    }

    @Inject(method = "renderAccelerated" + TACZFIXES_ACCEL_DESC, at = @At(value = "INVOKE",
            target = TACZFIXES_ACCEL_SUPER_TARGET, remap = false), remap = false)
    private void taczfixes$renderStandbySlotsAccelerated(PoseStack pose, ItemStack itemStack,
                                                         ItemDisplayContext displayContext, RenderType renderType,
                                                         int light, int overlay,
                                                         net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource,
                                                         CallbackInfo ci) {
        renderStandby(pose, displayContext, light, overlay);
    }

    /**
     * standby 槽位(玩家未使用的瞄具): 瞄具 ocular 作为普通 cube 渲染, 其它不做任何处理。
     * active 槽位(玩家使用中的瞄具)按 TACZ 原生功能路径渲染。
     */
    @Unique
    private void renderStandby(PoseStack pose, ItemDisplayContext displayContext,
                               int light, int overlay) {
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        // 1) active 槽位(玩家使用中的自定义瞄具): 先渲染, 写镜内参考值+分划
        renderActiveSlot(pose, displayContext, light, overlay);
        // 2) 渲染完 active 后即设置模板剔除语义, 使其后所有内容(standby 瞄具、配件、枪身)受剔除。
        taczfixes$applyActiveScopeStencil(gun);
        com.mojang.blaze3d.systems.RenderSystem.stencilOp(org.lwjgl.opengl.GL11.GL_KEEP,
                org.lwjgl.opengl.GL11.GL_KEEP, org.lwjgl.opengl.GL11.GL_KEEP);
        // 3) standby 瞄具(未使用)渲染: displayContext(ocular 正常显示为普通 cube)。
        //    第一人称时 renderScope 内部会 clearStencil, 破坏 active 参考值。
        List<Object[]> standby = StandbySlotBuffer.takePending();
        for (Object[] slot : standby) {
            renderStandbySlot((ItemStack) slot[0], gun,
                    (BedrockPart) slot[1],
                    (String) slot[2],
                    pose, displayContext, light, overlay);
        }
        // 4) standby 渲染破坏参考值后: 重写参考值+语义, 保证 super.render 内渲染被剔。
        //    active 存在 → 重渲染 active; 无 active(默认槽镜/铁瞄) → 复刻 TACZ 标准槽镜渲染。
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

    /** 无 active 槽时: 复刻 TACZ 标准槽 scope 渲染(scopePosPath + renderAttachment), 重写镜内参考值。 */
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

    /**
     * active 槽位(玩家使用中的自定义瞄具): 与原生槽瞄具一致——按调用方 displayContext 渲染,
     * 瞄准时为第一人称即走 TACZ renderScope/renderBoth(写镜片 stencil + 分划), ocular 不遮挡画面。
     */
    @Unique
    private void renderActiveSlot(PoseStack pose, ItemDisplayContext displayContext,
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
        StandbySlotBuffer.applySlotAdapterOffset(actItem, gun, active, pose, displayContext, light, overlay);
        IAttachment ia = IAttachment.getIAttachmentOrNull(actItem);
        if (ia != null) {
            ResourceLocation actId = ia.getAttachmentId(actItem);
            TimelessAPI.getClientAttachmentIndex(actId).ifPresent(index -> {
                com.tacz.guns.client.model.BedrockAttachmentModel actModel = index.getAttachmentModel();
                ResourceLocation actTexture = index.getModelTexture();
                if (actModel != null && actTexture != null) {
                    RenderType renderType = RenderType.entityCutout(actTexture);
                    actModel.render(actItem, gun, pose, displayContext, renderType, light, overlay);
                }
            });
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
                // 组合镜: renderBoth 只把 ocular_scope 区域写成 >127 的高 stencil 值,
                // 用 GREATER 127 只剔除 ocular_scope 区域, ocular_sight 区域(低值)保留。
                com.tacz.guns.util.RenderHelper.enableItemEntityStencilTest();
                com.mojang.blaze3d.systems.RenderSystem.stencilFunc(org.lwjgl.opengl.GL11.GL_GREATER, 127, 255);
            } else if (index.isScope()) {
                // 纯筒状镜: 镜片外(EQUAL 0)剔除
                com.tacz.guns.util.RenderHelper.enableItemEntityStencilTest();
                com.mojang.blaze3d.systems.RenderSystem.stencilFunc(org.lwjgl.opengl.GL11.GL_EQUAL, 0, 255);
            }
            // sight 型(红点/全息): 与 TACZ 原版一致, 不设置任何模板语义, 不剔除
        });
    }

    @Unique
    private void renderStandbySlot(ItemStack item, ItemStack gun, BedrockPart node,
                                   String slotId, PoseStack pose, ItemDisplayContext displayContext,
                                   int light, int overlay) {
        if (node == null) return;
        if (!isScopeLikeAttachment(item)) {
            // 非瞄具配件(laser/grip 等): 走原版 renderSlotAttachment 管线(含 mount 处理), 位置正确
            StandbySlotBuffer.renderSlotAttachment(item, gun, node, pose, displayContext, light, overlay, slotId);
            return;
        }
        pose.pushPose();
        StandbySlotBuffer.applyNodePathTransform(node, pose);
        pose.translate(0.0F, -1.5F, 0.0F);
        StandbySlotBuffer.applySlotAdapterOffset(item, gun, slotId, pose, displayContext, light, overlay);
        // 与原生槽一致: 用 AttachmentRender.renderAttachment 管线(内部含 LOD 与 mount 处理),
        // 传入调用方 displayContext 保持与原生槽相同的第一人称/第三人称/LOD 判定。
        renderLikeNative(item, gun, pose, displayContext, light, overlay);
        pose.popPose();
    }

    /** 与原生槽瞄具同管线渲染: standby(未使用)瞄具以 displayContext 渲染(ocular 正确),
     * 但渲染前后关闭/恢复 isScope/isSight, 功能分支(renderScope 等)不会执行,
     * 不触碰模板(active 参考值与剔除语义完好); 渲染前完整激活瞄具专属节点使几何可见。 */
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
        com.tacz.guns.client.model.BedrockAttachmentModel model = idx.get().getAttachmentModel();
        ResourceLocation texture = idx.get().getModelTexture();
        if (model == null || texture == null) return;
        java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> restore = new java.util.ArrayList<>();
        // standby(未使用)的组合镜: ocular_scope 应渲染为普通 cube, ocular_sight 隐藏。
        StandbySlotBuffer.ensureScopeOcularVisible(model, restore);
        MixinBedrockAttachmentModelScopeSuppress acc = (MixinBedrockAttachmentModelScopeSuppress) model;
        // 完整激活瞄具专属节点(scopeBody/ocularRing/ocular 末节点), 否则 super.render 不渲染;
        // division(分划板)不激活且强制隐藏——standby 瞄具不应显示分划。
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
    }

    @Unique
    private static void taczfixes$activateIfPresent(java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> restore,
                                                    java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> path) {
        if (path == null || path.isEmpty()) return;
        com.tacz.guns.client.model.bedrock.BedrockPart part = path.get(path.size() - 1);
        if (!part.visible) {
            part.visible = true;
            restore.add(part);
        }
    }

    @Unique
    private static void taczfixes$hideIfPresent(java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> restore,
                                                java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> path) {
        if (path == null || path.isEmpty()) return;
        com.tacz.guns.client.model.bedrock.BedrockPart part = path.get(path.size() - 1);
        if (part.visible) {
            part.visible = false;
            restore.add(part);
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

    /** 机瞄折叠: TACZ scopeHiddenRender 每帧按 currentAttachmentItem 的 SCOPE 值判定 SIGHT/SIGHT_FOLDED
     * 可见性; active 模式我们将其置 EMPTY 导致默认槽有镜时机瞄不折叠。
     * 拦截其 EnumMap.get(SCOPE), 返回标准槽真实镜值, 使 TACZ 判定恢复正确。 */
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
