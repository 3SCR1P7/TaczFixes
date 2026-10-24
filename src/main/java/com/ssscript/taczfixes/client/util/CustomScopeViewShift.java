package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**



 */
public final class CustomScopeViewShift {

    private static final float SMOOTH_FACTOR = 0.2f;
    /** 与 SMOOTH_FACTOR(60fps 每帧 0.2) 等效的时间常数, 用于按帧间隔做平滑, 避免高帧率下收敛过快。 */
    private static final float SMOOTH_TIME_CONSTANT = 0.075f;
    private static long smoothLastFrameNanos = 0L;
    private static String prevSlot = "";
    private static Vec3 smoothPivot = Vec3.ZERO;
    private static Vec3 smoothShift = Vec3.ZERO;
    private static float smoothAngle = 0f;
    private static float smoothOffset = 0f;
    private static boolean pushed = false;
    private static final java.util.Map<ResourceLocation, java.util.Optional<org.joml.Vector3f>> ADAPTER_OFFSET_CACHE = new java.util.HashMap<>();
    private static ItemStack stdScopeCacheGun;
    private static int stdScopeCacheHash;
    private static ItemStack stdScopeCache;

    private CustomScopeViewShift() {
    }

    private static float smoothFactor() {
        long now = System.nanoTime();
        float dt = smoothLastFrameNanos == 0L ? 1f / 60f : (now - smoothLastFrameNanos) / 1.0e9f;
        if (dt <= 0f || dt > 0.1f) {
            dt = 1f / 60f;
        }
        smoothLastFrameNanos = now;
        float factor = (float) (1.0d - Math.exp(-dt / SMOOTH_TIME_CONSTANT));
        return Math.max(0.01f, Math.min(factor, 1f));
    }


    public static void apply(PoseStack poseStack, BedrockGunModel model, ItemDisplayContext displayContext) {
        pushed = false;
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
            prevSlot = "";
            smoothPivot = Vec3.ZERO;
            smoothShift = Vec3.ZERO;
            smoothAngle = 0f;
            smoothOffset = 0f;
            return;
        }
        ItemStack gun = model.getCurrentGunItem();
        if (gun == null || gun.isEmpty()) {
            return;
        }
        String active = ScopeSwitchState.getActiveSlot(gun);
        float slotAngle = 0f;
        float slotOffset = 0f;
        if (active != null) {
            IGun igunLocal = IGun.getIGunOrNull(gun);
            if (igunLocal != null) {
                CustomSlotDefinition def = CustomSlotManager.getSlots(gun).get(active);
                if (def != null && def.type != null && "scope".equalsIgnoreCase(def.type)) {
                    slotAngle = def.angle;
                    slotOffset = def.offset;
                }
            }
        }
        Vec3 target = Vec3.ZERO;
        Vec3 actPos = null;
        List<com.tacz.guns.client.model.bedrock.BedrockPart> scopePosPath = model.getScopePosPath();
        if (active != null && scopePosPath != null && !scopePosPath.isEmpty()) {
            com.tacz.guns.client.model.bedrock.BedrockPart stdNode = scopePosPath.get(scopePosPath.size() - 1);
            CustomSlotManager.SlotEntry activeEntry = CustomSlotManager.getEntry(gun, active);
            com.tacz.guns.client.model.bedrock.BedrockPart actNode =
                    activeEntry == null ? null : CustomSlotMount.nodeFor(model, activeEntry, active);
            if (stdNode != null && actNode != null) {
                ItemStack stdItem = readStandardScope(gun);
                if (stdItem.isEmpty()) {
                    IGun igunLocal = IGun.getIGunOrNull(gun);
                    if (igunLocal != null) {
                        stdItem = igunLocal.getBuiltinAttachment(gun, AttachmentType.SCOPE);
                    }
                }
                ItemStack actItem = CustomSlotStorage.get(gun, active);
                // 相机视图已经切换到自定义槽瞄具, 标准挂载点的目标也要按该瞄具的视图路径计算。
                boolean actScope = isScopeAttachment(actItem);
                Vector3f adapterOffset = getActiveSlotAdapterOffset(gun, actItem, active);
                Vector3f stdAdapterOffset = getStandardScopeAdapterOffset(gun, stdItem);
                Vec3 stdPos = slotCenterWorld(stdNode, actScope ? actItem : stdItem, gun, stdAdapterOffset);
                actPos = slotCenterWorld(actNode, actItem, gun, adapterOffset,
                        activeEntry == null ? null : CustomSlotMount.sourceChain(model, gun, activeEntry),
                        activeEntry != null && !activeEntry.source().isEmpty(), -1);
                if (actScope) {
                    target = stdPos.subtract(actPos);
                } else if (stdItem.isEmpty()) {
                    List<com.tacz.guns.client.model.bedrock.BedrockPart> ironPath = model.getIronSightPath();
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
        Vec3 targetS = actPos != null ? target : Vec3.ZERO;
        float targetTh = actPos != null ? slotAngle : 0f;
        float targetO = actPos != null ? slotOffset : 0f;

        String slotKey = active == null ? "" : active;
        boolean viewChanged = actPos != null && smoothPivot.distanceToSqr(actPos) > 1.0E-4;
        if (!slotKey.equals(prevSlot) || viewChanged) {
            // pivot 固定为当前目标瞄具的 pivot, 用 shift/angle 表示当前变换。
            // 这样 angle=0 -> angle!=0 时不会绕旧 pivot 产生错误的弧线, 同时切换瞬间仍连续。
            Vec3 gCur = oldG(smoothPivot, smoothShift, smoothAngle, smoothOffset);
            Vec3 newPivot = actPos != null ? pivot : smoothPivot;
            smoothPivot = newPivot;
            double rad = Math.toRadians(smoothAngle);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            smoothShift = new Vec3(
                    (float) (gCur.x - newPivot.x + (cos * newPivot.x - sin * newPivot.y)),
                    (float) (gCur.y - newPivot.y + (sin * newPivot.x + cos * newPivot.y)),
                    (float) gCur.z);
            smoothOffset = 0f;
            prevSlot = slotKey;
        }

        float smooth = smoothFactor();
        if (progress < 0.05f) {
            // 开镜刚开始: 直接对齐, 偏移被开镜进度掩盖。
            smoothPivot = pivot;
            smoothShift = targetS;
            smoothAngle = targetTh;
            smoothOffset = targetO;
        } else {
            smoothShift = smoothShift.add(targetS.subtract(smoothShift).scale(smooth));
            smoothAngle += (targetTh - smoothAngle) * smooth;
            smoothOffset += (targetO - smoothOffset) * smooth;
        }

        // 原有参数插值: pivot 与角度分别插值, 旋转和平移在每一帧由同一组参数生成。
        Vec3 shift = smoothShift.scale(progress);
        float angle = smoothAngle * progress;
        float offset = smoothOffset * progress;
        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        Vec3 p = smoothPivot;
        Vec3 g = new Vec3(
                p.x + shift.x - (cos * p.x - sin * p.y),
                p.y + shift.y - (sin * p.x + cos * p.y),
                shift.z - offset);
        if (g.lengthSqr() < 1.0E-8 && Math.abs(angle) < 0.001f) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(g.x, g.y, g.z);
        if (Math.abs(angle) > 0.001f) {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
        }
        pushed = true;
    }
    private static Vec3 oldG(Vec3 p, Vec3 shift, float angleDeg, float offset) {
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3(
                p.x + shift.x - (cos * p.x - sin * p.y),
                p.y + shift.y - (sin * p.x + cos * p.y),
                shift.z - offset);
    }
    public static void pop(PoseStack poseStack) {
        if (pushed) {
            poseStack.popPose();
            pushed = false;
        }
    }

    public static boolean isScopeAttachment(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return false;
        return TimelessAPI.getClientAttachmentIndex(ia.getAttachmentId(item))
                .map(ClientAttachmentIndex::isScope).orElse(false);
    }


    public static ItemStack readStandardScope(ItemStack gun) {
        if (gun == null) return ItemStack.EMPTY;
        CompoundTag tag = gun.getTag();
        int hash = tag == null ? 0 : tag.hashCode();
        if (gun == stdScopeCacheGun && hash == stdScopeCacheHash) {
            return stdScopeCache;
        }
        ItemStack result = ItemStack.EMPTY;
        if (tag != null) {
            String key = com.tacz.guns.api.item.nbt.GunItemDataAccessor.GUN_ATTACHMENT_BASE + AttachmentType.SCOPE.name();
            if (tag.contains(key, 10)) {
                ItemStack scope = ItemStack.of(tag.getCompound(key));
                if (!scope.isEmpty()) result = scope;
            }
        }
        stdScopeCacheGun = gun;
        stdScopeCacheHash = hash;
        stdScopeCache = result;
        return result;
    }

    private static Vec3 slotCenterWorld(com.tacz.guns.client.model.bedrock.BedrockPart slotNode,
                                        ItemStack attachmentItem, ItemStack gun, Vector3f adapterOffset) {
        return slotCenterWorld(slotNode, attachmentItem, gun, adapterOffset, null, false);
    }

    private static Vec3 slotCenterWorld(com.tacz.guns.client.model.bedrock.BedrockPart slotNode,
                                        ItemStack attachmentItem, ItemStack gun, Vector3f adapterOffset,
                                        List<CustomSlotMount.Step> mountChain, boolean attachmentNode) {
        return slotCenterWorld(slotNode, attachmentItem, gun, adapterOffset, mountChain, attachmentNode, -1);
    }

    private static Vec3 slotCenterWorld(com.tacz.guns.client.model.bedrock.BedrockPart slotNode,
                                        ItemStack attachmentItem, ItemStack gun, Vector3f adapterOffset,
                                        List<CustomSlotMount.Step> mountChain, boolean attachmentNode,
                                        int viewIndexOverride) {
        List<com.tacz.guns.client.model.bedrock.BedrockPart> chain = new ArrayList<>();
        com.tacz.guns.client.model.bedrock.BedrockPart cur = slotNode;
        while (cur != null) {
            chain.add(cur);
            cur = cur.getParent();
        }
        PoseStack ps = new PoseStack();
        CustomSlotMount.applyTransforms(ps, mountChain, gun);
        if (attachmentNode) {
            ps.translate(0.0F, -1.5F, 0.0F);
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            chain.get(i).translateAndRotateAndScale(ps);
        }
        if (adapterOffset != null) {
            ps.translate(adapterOffset.x / 16.0F, -adapterOffset.y / 16.0F, adapterOffset.z / 16.0F);
        }
        if (attachmentItem != null && !attachmentItem.isEmpty()) {
            IAttachment ia = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (ia != null) {
                Optional<ClientAttachmentIndex> indexOpt = TimelessAPI.getClientAttachmentIndex(ia.getAttachmentId(attachmentItem));
                if (indexOpt.isPresent()) {
                    ClientAttachmentIndex index = indexOpt.get();
                    com.tacz.guns.client.model.BedrockAttachmentModel attachmentModel = index.getAttachmentModel();
                    if (attachmentModel != null) {
                        applyViewPath(index, attachmentModel, ps, attachmentItem, viewIndexOverride);
                    }
                }
            }
        }
        Vector3f v = ps.last().pose().transformPosition(0f, 0f, 0f, new Vector3f());
        return new Vec3(v.x(), v.y(), v.z());
    }


    private static Vector3f getActiveSlotAdapterOffset(ItemStack gun, ItemStack actItem, String activeSlot) {
        if (actItem == null || actItem.isEmpty()) return null;
        IAttachment ia = IAttachment.getIAttachmentOrNull(actItem);
        if (ia == null) return null;
        ResourceLocation attachmentId = ia.getAttachmentId(actItem);
        ResourceLocation adapterId = CustomSlotStorage.getAdapter(gun, activeSlot);
        if (adapterId == null) return null;
        if (!TimelessAPI.getCommonSlotAdapterIndex(adapterId)
                .map(idx -> idx.allowsAttachment(attachmentId)).orElse(false)) {
            return null;
        }
        return ADAPTER_OFFSET_CACHE.computeIfAbsent(adapterId, id ->
                TimelessAPI.getClientSlotAdapterIndex(id)
                        .map(com.tacz.guns.client.resource.index.ClientSlotAdapterIndex::getMountOffset))
                .orElse(null);
    }

    private static Vector3f getStandardScopeAdapterOffset(ItemStack gun, ItemStack stdItem) {
        if (gun == null || gun.isEmpty()) return null;
        if (stdItem == null || stdItem.isEmpty()) return null;
        IAttachment ia = IAttachment.getIAttachmentOrNull(stdItem);
        if (ia == null) return null;
        ResourceLocation attachmentId = ia.getAttachmentId(stdItem);
        if (attachmentId == null) return null;
        ResourceLocation adapterId = com.tacz.guns.util.SlotAdapterHelper.getEffectiveSlotAdapter(gun, AttachmentType.SCOPE, attachmentId);
        if (adapterId == null) return null;
        return ADAPTER_OFFSET_CACHE.computeIfAbsent(adapterId, id ->
                TimelessAPI.getClientSlotAdapterIndex(id)
                        .map(com.tacz.guns.client.resource.index.ClientSlotAdapterIndex::getMountOffset))
                .orElse(null);
    }

    private static void applyViewPath(ClientAttachmentIndex index, com.tacz.guns.client.model.BedrockAttachmentModel attachmentModel,
                                      PoseStack ps, ItemStack attachmentItem) {
        applyViewPath(index, attachmentModel, ps, attachmentItem, -1);
    }

    private static void applyViewPath(ClientAttachmentIndex index, com.tacz.guns.client.model.BedrockAttachmentModel attachmentModel,
                                      PoseStack ps, ItemStack attachmentItem, int viewIndexOverride) {
        if (attachmentModel == null) return;
        ps.translate(0f, -1.5f, 0f);
        com.tacz.guns.client.model.bedrock.BedrockPart root = attachmentModel.getRootNode();
        if (root != null) root.translateAndRotateAndScale(ps);
        int viewIndex = viewIndexOverride;
        if (viewIndex < 0) {
            viewIndex = 0;
            int[] views = index.getViews();
            if (views != null && views.length > 0 && attachmentItem.getTag() != null) {
                int zoom = com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor.getZoomNumberFromTag(attachmentItem.getTag());
                viewIndex = views[zoom % views.length] - 1;
                if (viewIndex < 0) viewIndex = 0;
            }
        }
        List<com.tacz.guns.client.model.bedrock.BedrockPart> view = attachmentModel.getScopeViewPath(viewIndex);
        if (view == null) return;
        for (com.tacz.guns.client.model.bedrock.BedrockPart part : view) {
            if (part != null) part.translateAndRotateAndScale(ps);
        }
    }
}
