package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.client.util.LensDepthWriter;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.client.util.SwitchedDisplayManager;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
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
import java.util.Optional;
import java.util.Set;

@Mixin(BedrockGunModel.class)
public abstract class MixinBedrockGunModelCustomSlot {

    @Shadow(remap = false) private EnumMap<AttachmentType, ItemStack> currentAttachmentItem;
    @Shadow(remap = false) private ItemStack currentGunItem;
    @Shadow(remap = false) private Set<String> adapterToRender;
    @Shadow(remap = false) protected List<com.tacz.guns.client.model.bedrock.BedrockPart> scopePosPath;

    @Unique
    private boolean taczfixes$scopeViewShifted = false;
    @Unique
    private static final float TACZFIXES_SMOOTH_FACTOR = 0.2f;
    @Unique
    private static String taczfixes$prevSlot = "";
    @Unique
    private static int taczfixes$animMode = 1;
    @Unique
    private static Vec3 taczfixes$smoothPivot = Vec3.ZERO;
    @Unique
    private static Vec3 taczfixes$smoothShift = Vec3.ZERO;
    @Unique
    private static float taczfixes$smoothAngle = 0f;
    @Unique
    private static float taczfixes$smoothOffset = 0f;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/compat/ar/ARCompat;shouldAccelerate()Z", remap = false), remap = false)
    private void taczfixes$customSlotRender(PoseStack poseStack, ItemStack itemStack, ItemDisplayContext displayContext, RenderType renderType, int light, int overlay, float red, float green, float blue, float alpha, net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
        handleScopeViewShift(poseStack, displayContext);
        handleCustomSlots(poseStack, displayContext, light, overlay, bufferSource);
        // 标准槽瞄准(无自定义槽 active)场景: 放行标准槽火控附件在第一人称渲染时绘制预测框;
        // render 末尾由 renderEnd 恢复为 false
        boolean aiming = taczfixes$isAiming(itemStack);
        boolean customActive = ScopeSwitchState.getActiveSlot(itemStack) != null;
        if (aiming && !customActive) {
            com.ssscript.taczfixes.client.util.RangefinderDrawBudget.setAimingScene(true);
        }
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            at = @At("RETURN"), remap = false)
    private void taczfixes$renderEnd(PoseStack poseStack, ItemStack itemStack, ItemDisplayContext displayContext, RenderType renderType, int light, int overlay, float red, float green, float blue, float alpha, net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
        com.ssscript.taczfixes.client.util.RangefinderDrawBudget.setAimingScene(false);
        taczfixes$popScopeShift(poseStack);
    }

    @Unique
    private void taczfixes$popScopeShift(PoseStack poseStack) {
        if (this.taczfixes$scopeViewShifted) {
            poseStack.popPose();
            this.taczfixes$scopeViewShifted = false;
        }
    }

    @Unique
    private void handleScopeViewShift(PoseStack poseStack, ItemDisplayContext displayContext) {
        this.taczfixes$scopeViewShifted = false;
        if (displayContext != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return;
        }
        if (!(Minecraft.getInstance().getCameraEntity() instanceof LocalPlayer player) || !player.isAlive()) {
            return;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null) {
            return;
        }
        float progress = ScopeSwitchState.aimingProgressValue;
        if (progress < 0.001f) {
            float opProgress = operator.getClientAimingProgress(Minecraft.getInstance().getFrameTime());
            if (opProgress > progress) {
                progress = opProgress;
            }
        }
        if (progress < 0.001f) {
            taczfixes$prevSlot = "";
            taczfixes$animMode = 1;
            taczfixes$smoothShift = Vec3.ZERO;
            taczfixes$smoothAngle = 0f;
            taczfixes$smoothOffset = 0f;
            return;
        }
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) {
            return;
        }
        String active = ScopeSwitchState.getActiveSlot(gun);
        float slotAngle = 0f;
        float slotOffset = 0f;
        if (active != null) {
            IGun igunLocal = IGun.getIGunOrNull(gun);
            if (igunLocal != null) {
                com.ssscript.taczfixes.common.data.CustomSlotDefinition def = CustomSlotManager.getSlots(igunLocal.getGunId(gun)).get(active);
                if (def != null) {
                    slotAngle = def.angle;
                    slotOffset = def.offset;
                }
            }
        }
        Vec3 target = Vec3.ZERO;
        Vec3 actPos = null;
        if (active != null && this.scopePosPath != null && !this.scopePosPath.isEmpty()) {
            com.tacz.guns.client.model.bedrock.BedrockPart stdNode = this.scopePosPath.get(this.scopePosPath.size() - 1);
            BedrockAnimatedModel self = (BedrockAnimatedModel) (Object) this;
            com.tacz.guns.client.model.bedrock.BedrockPart actNode = self.getNode(active + "_pos");
            if (stdNode != null && actNode != null) {
                ItemStack stdItem = readStandardScope(gun);
                if (stdItem.isEmpty()) {
                    IGun igunLocal = IGun.getIGunOrNull(gun);
                    if (igunLocal != null) {
                        stdItem = igunLocal.getBuiltinAttachment(gun, AttachmentType.SCOPE);
                    }
                }
                ItemStack actItem = CustomSlotStorage.get(gun, active);
                Vector3f adapterOffset = getActiveSlotAdapterOffset(gun, actItem);
                // 标准 scope 槽的有效适配器偏移(与 AttachmentRender.renderAttachment 内
                // getSlotAdapter + applyMountOffset 一致): 标准 scope 渲染时应用该偏移,
                // 若此处取 null 则基准位置与真实渲染错位, 会污染所有自定义槽瞄具的平移量
                Vector3f stdAdapterOffset = getStandardScopeAdapterOffset(gun, stdItem);
                Vec3 stdPos = slotCenterWorld(stdNode, stdItem, gun, stdAdapterOffset);
                actPos = slotCenterWorld(actNode, actItem, gun, adapterOffset);
                if (stdItem.isEmpty()) {
                    java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> ironPath = ((BedrockGunModel) (Object) this).getIronSightPath();
                    if (ironPath != null && !ironPath.isEmpty()) {
                        Vec3 ironPos = slotCenterWorld(ironPath.get(ironPath.size() - 1), ItemStack.EMPTY, gun, null);
                        target = ironPos.subtract(actPos);
                    } else {
                        target = new Vec3(-actPos.x, -actPos.y, 0.0);
                    }
                } else {
                    target = stdPos.subtract(actPos);
                }
            }
        }
        Vec3 pivot = actPos != null ? actPos : Vec3.ZERO;
        Vec3 t = actPos != null ? target : Vec3.ZERO;
        float theta = actPos != null ? slotAngle : 0f;
        float off = actPos != null ? slotOffset : 0f;
        Vec3 targetS = new Vec3(t.x * progress, t.y * progress, t.z * progress);
        float targetTh = theta * progress;
        float targetO = off * progress;
        String slotKey = active == null ? "" : active;
        if (!slotKey.equals(taczfixes$prevSlot)) {
            String oldKey = taczfixes$prevSlot;
            taczfixes$prevSlot = slotKey;
            taczfixes$animMode = (oldKey.isEmpty() || slotKey.isEmpty()) ? 1 : 2;
        }
        if (taczfixes$animMode == 1) {
            if (active != null) {
                taczfixes$smoothPivot = pivot;
            }
        } else {
            taczfixes$smoothPivot = taczfixes$smoothPivot.add(pivot.subtract(taczfixes$smoothPivot).scale(TACZFIXES_SMOOTH_FACTOR));
        }
        taczfixes$smoothShift = taczfixes$smoothShift.add(targetS.subtract(taczfixes$smoothShift).scale(TACZFIXES_SMOOTH_FACTOR));
        taczfixes$smoothAngle += (targetTh - taczfixes$smoothAngle) * TACZFIXES_SMOOTH_FACTOR;
        taczfixes$smoothOffset += (targetO - taczfixes$smoothOffset) * TACZFIXES_SMOOTH_FACTOR;
        double rad = Math.toRadians(taczfixes$smoothAngle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        Vec3 p = taczfixes$smoothPivot;
        Vec3 g = new Vec3(
                p.x + taczfixes$smoothShift.x - (cos * p.x - sin * p.y),
                p.y + taczfixes$smoothShift.y - (sin * p.x + cos * p.y),
                taczfixes$smoothShift.z - taczfixes$smoothOffset);
        if (g.lengthSqr() < 1.0E-8 && Math.abs(taczfixes$smoothAngle) < 0.001f) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(g.x, g.y, g.z);
        if (Math.abs(taczfixes$smoothAngle) > 0.001f) {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(taczfixes$smoothAngle));
        }
        this.taczfixes$scopeViewShifted = true;
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

    @Unique
    private static boolean isScopeAttachment(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return false;
        return TimelessAPI.getClientAttachmentIndex(ia.getAttachmentId(item))
                .map(ClientAttachmentIndex::isScope).orElse(false);
    }

    @Unique
    private static Vec3 slotCenterWorld(com.tacz.guns.client.model.bedrock.BedrockPart slotNode,
                                        ItemStack attachmentItem, ItemStack gun, Vector3f adapterOffset) {
        List<com.tacz.guns.client.model.bedrock.BedrockPart> chain = new ArrayList<>();
        com.tacz.guns.client.model.bedrock.BedrockPart cur = slotNode;
        while (cur != null) {
            chain.add(cur);
            cur = cur.getParent();
        }
        PoseStack ps = new PoseStack();
        for (int i = chain.size() - 1; i >= 0; i--) {
            chain.get(i).translateAndRotateAndScale(ps);
        }
        // 适配器挂载偏移(与 AttachmentRender.applyMountOffset 一致, 模型空间):
        // 渲染配件时在节点链上叠加 translate(x/16, -y/16, z/16), 视角枢轴须同步
        if (adapterOffset != null) {
            ps.translate(adapterOffset.x / 16.0F, -adapterOffset.y / 16.0F, adapterOffset.z / 16.0F);
        }
        if (attachmentItem != null && !attachmentItem.isEmpty()) {
            IAttachment ia = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (ia != null) {
                Optional<ClientAttachmentIndex> indexOpt = SwitchedDisplayManager.getClientAttachmentIndex(gun, ia.getAttachmentId(attachmentItem));
                if (!indexOpt.isPresent()) {
                    indexOpt = TimelessAPI.getClientAttachmentIndex(ia.getAttachmentId(attachmentItem));
                }
                if (indexOpt.isPresent()) {
                    ClientAttachmentIndex index = indexOpt.get();
                    com.tacz.guns.client.model.BedrockAttachmentModel model = index.getAttachmentModel();
                    if (model != null) {
                        applyViewPath(index, model, ps, attachmentItem);
                    }
                }
            }
        }
        Vector3f v = ps.last().pose().transformPosition(0f, 0f, 0f, new Vector3f());
        return new Vec3(v.x(), v.y(), v.z());
    }

    /**
     * 当前激活槽位配件的有效适配器 mountOffset(与 FirstPersonRenderGunEvent.getScopeMountOffset 一致):
     * 按槽位适配器存在且允许该配件时, 返回客户端适配器索引的 mountOffset。
     */
    @Unique
    private static Vector3f getActiveSlotAdapterOffset(ItemStack gun, ItemStack actItem) {
        if (actItem == null || actItem.isEmpty()) return null;
        IAttachment ia = IAttachment.getIAttachmentOrNull(actItem);
        if (ia == null) return null;
        ResourceLocation attachmentId = ia.getAttachmentId(actItem);
        ResourceLocation adapterId = CustomSlotStorage.getAdapter(gun, ScopeSwitchState.getActiveSlot(gun));
        if (adapterId == null) return null;
        if (!TimelessAPI.getCommonSlotAdapterIndex(adapterId)
                .map(idx -> idx.allowsAttachment(attachmentId)).orElse(false)) {
            return null;
        }
        return TimelessAPI.getClientSlotAdapterIndex(adapterId)
                .map(com.tacz.guns.client.resource.index.ClientSlotAdapterIndex::getMountOffset)
                .orElse(null);
    }

    /**
     * 标准 scope 槽的有效适配器 mountOffset:
     * 与 TACZ 原版 AttachmentRender.renderAttachment 中的 getSlotAdapter + applyMountOffset 一致,
     * 保证 slotCenterWorld 的基准位置与实际渲染位置同步。
     */
    @Unique
    private static Vector3f getStandardScopeAdapterOffset(ItemStack gun, ItemStack stdItem) {
        if (gun == null || gun.isEmpty()) return null;
        if (stdItem == null || stdItem.isEmpty()) return null;
        IAttachment ia = IAttachment.getIAttachmentOrNull(stdItem);
        if (ia == null) return null;
        ResourceLocation attachmentId = ia.getAttachmentId(stdItem);
        if (attachmentId == null) return null;
        ResourceLocation adapterId = com.tacz.guns.util.SlotAdapterHelper.getEffectiveSlotAdapter(gun, AttachmentType.SCOPE, attachmentId);
        if (adapterId == null) return null;
        return TimelessAPI.getClientSlotAdapterIndex(adapterId)
                .map(com.tacz.guns.client.resource.index.ClientSlotAdapterIndex::getMountOffset)
                .orElse(null);
    }

    @Unique
    private static void applyViewPath(ClientAttachmentIndex index, com.tacz.guns.client.model.BedrockAttachmentModel model, PoseStack ps,
                                      ItemStack attachmentItem) {
        if (model == null) return;
        ps.translate(0f, -1.5f, 0f);
        com.tacz.guns.client.model.bedrock.BedrockPart root = model.getRootNode();
        if (root != null) root.translateAndRotateAndScale(ps);
        int[] views = index.getViews();
        int viewIndex = 0;
        if (views != null && views.length > 0 && attachmentItem.getTag() != null) {
            int zoom = com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor.getZoomNumberFromTag(attachmentItem.getTag());
            viewIndex = views[zoom % views.length] - 1;
            if (viewIndex < 0) viewIndex = 0;
        }
        List<com.tacz.guns.client.model.bedrock.BedrockPart> view = model.getScopeViewPath(viewIndex);
        if (view == null) return;
        for (com.tacz.guns.client.model.bedrock.BedrockPart p : view) {
            if (p != null) p.translateAndRotateAndScale(ps);
        }
    }

    @Unique
    private static Vec3 nodeWorldPos(com.tacz.guns.client.model.bedrock.BedrockPart node) {
        List<com.tacz.guns.client.model.bedrock.BedrockPart> chain = new ArrayList<>();
        com.tacz.guns.client.model.bedrock.BedrockPart cur = node;
        while (cur != null) {
            chain.add(cur);
            cur = cur.getParent();
        }
        PoseStack ps = new PoseStack();
        for (int i = chain.size() - 1; i >= 0; i--) {
            chain.get(i).translateAndRotateAndScale(ps);
        }
        Vector3f v = ps.last().pose().transformPosition(0f, 0f, 0f, new Vector3f());
        return new Vec3(v.x(), v.y(), v.z());
    }

    private void handleCustomSlots(PoseStack poseStack, ItemDisplayContext displayContext, int light, int overlay, net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource) {
        ItemStack gun = this.currentGunItem;
        if (gun == null || gun.isEmpty()) return;
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) return;
        ResourceLocation gunId = igun.getGunId(gun);
        Map<String, CustomSlotDefinition> slots = CustomSlotManager.getSlots(gunId);
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
            ItemStack standardScope = readStandardScope(gun);
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
            if (accelerated) {
                if (this.currentAttachmentItem != null && !actItem.isEmpty()) {
                    this.currentAttachmentItem.put(AttachmentType.SCOPE, actItem);
                }
                com.tacz.guns.client.model.bedrock.BedrockPart actNode = self.getNode(active + "_pos");
                if (actNode != null && isScopeAttachment(actItem)) {
                    poseStack.pushPose();
                    applyNodePathTransform(actNode, poseStack);
                    LensDepthWriter.writeLensDepth(actItem, gun, poseStack, displayContext, light, overlay);
                    poseStack.popPose();
                }
            }
        }

        List<Object[]> standby = new ArrayList<>();
        for (Map.Entry<String, CustomSlotDefinition> entry : slots.entrySet()) {
            String slotId = entry.getKey();
            if (slotId.equals(active)) continue;
            ItemStack item = CustomSlotStorage.get(gun, slotId);
            if (item.isEmpty()) continue;
            com.tacz.guns.client.model.bedrock.BedrockPart node = self.getNode(slotId + "_pos");
            if (node == null) continue;
            standby.add(new Object[]{item, node, slotId});
        }
        com.ssscript.taczfixes.client.util.StandbySlotBuffer.setPending(standby);
    }

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

    private static void applyNodePathTransform(com.tacz.guns.client.model.bedrock.BedrockPart node,
                                               PoseStack pose) {
        java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart> path = new java.util.ArrayList<>();
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