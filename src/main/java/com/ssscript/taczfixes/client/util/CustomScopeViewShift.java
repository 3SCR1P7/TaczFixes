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
import com.tacz.guns.client.model.BedrockAnimatedModel;
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
 * 自定义槽瞄具开镜时整枪视图偏移(让自定义槽镜片对齐相机)。
 * 在 GunItemRendererWrapper.renderFirstPerson 的枪械变换之后、枪口粒子/火光定位之前应用,
 * 保证枪口火光与偏移后的枪身一致。
 */
public final class CustomScopeViewShift {

    private static final float SMOOTH_FACTOR = 0.2f;
    private static String prevSlot = "";
    private static int animMode = 1;
    private static Vec3 smoothPivot = Vec3.ZERO;
    private static Vec3 smoothShift = Vec3.ZERO;
    private static float smoothAngle = 0f;
    private static float smoothOffset = 0f;
    private static boolean pushed = false;

    private CustomScopeViewShift() {
    }

    /** 计算并压入偏移矩阵; 返回是否已压入。 */
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
            animMode = 1;
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
                CustomSlotDefinition def = CustomSlotManager.getSlots(igunLocal.getGunId(gun)).get(active);
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
            BedrockAnimatedModel self = model;
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
                Vector3f adapterOffset = getActiveSlotAdapterOffset(gun, actItem, active);
                Vector3f stdAdapterOffset = getStandardScopeAdapterOffset(gun, stdItem);
                Vec3 stdPos = slotCenterWorld(stdNode, stdItem, gun, stdAdapterOffset);
                actPos = slotCenterWorld(actNode, actItem, gun, adapterOffset);
                if (stdItem.isEmpty()) {
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
        Vec3 t = actPos != null ? target : Vec3.ZERO;
        float theta = actPos != null ? slotAngle : 0f;
        float off = actPos != null ? slotOffset : 0f;
        Vec3 targetS = new Vec3(t.x * progress, t.y * progress, t.z * progress);
        float targetTh = theta * progress;
        float targetO = off * progress;
        String slotKey = active == null ? "" : active;
        if (!slotKey.equals(prevSlot)) {
            String oldKey = prevSlot;
            prevSlot = slotKey;
            animMode = (oldKey.isEmpty() || slotKey.isEmpty()) ? 1 : 2;
        }
        if (animMode == 1) {
            if (active != null) {
                smoothPivot = pivot;
            }
        } else {
            smoothPivot = smoothPivot.add(pivot.subtract(smoothPivot).scale(SMOOTH_FACTOR));
        }
        smoothShift = smoothShift.add(targetS.subtract(smoothShift).scale(SMOOTH_FACTOR));
        smoothAngle += (targetTh - smoothAngle) * SMOOTH_FACTOR;
        smoothOffset += (targetO - smoothOffset) * SMOOTH_FACTOR;
        double rad = Math.toRadians(smoothAngle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        Vec3 p = smoothPivot;
        Vec3 g = new Vec3(
                p.x + smoothShift.x - (cos * p.x - sin * p.y),
                p.y + smoothShift.y - (sin * p.x + cos * p.y),
                smoothShift.z - smoothOffset);
        if (g.lengthSqr() < 1.0E-8 && Math.abs(smoothAngle) < 0.001f) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(g.x, g.y, g.z);
        if (Math.abs(smoothAngle) > 0.001f) {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(smoothAngle));
        }
        pushed = true;
    }

    /** 恢复 apply 时压入的矩阵。 */
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

    /** 瞄具的"眼睛位置"节点在模型空间中的坐标(无瞄具时用机瞄路径), 供开镜对位使用。 */
    public static Vec3 scopeEyePosition(com.tacz.guns.client.model.BedrockGunModel model, ItemStack gun) {
        if (model == null || gun == null || gun.isEmpty()) {
            return null;
        }
        ItemStack scope = readStandardScope(gun);
        if (scope.isEmpty()) {
            com.tacz.guns.api.item.IGun igun = com.tacz.guns.api.item.IGun.getIGunOrNull(gun);
            if (igun != null) {
                scope = igun.getBuiltinAttachment(gun, AttachmentType.SCOPE);
            }
        }
        if (scope.isEmpty()) {
            List<com.tacz.guns.client.model.bedrock.BedrockPart> ironPath = model.getIronSightPath();
            if (ironPath == null || ironPath.isEmpty()) {
                return null;
            }
            return slotCenterWorld(ironPath.get(ironPath.size() - 1), ItemStack.EMPTY, gun, null);
        }
        List<com.tacz.guns.client.model.bedrock.BedrockPart> scopePath = model.getScopePosPath();
        if (scopePath == null || scopePath.isEmpty()) {
            return null;
        }
        return slotCenterWorld(scopePath.get(scopePath.size() - 1), scope, gun, getStandardScopeAdapterOffset(gun, scope));
    }

    public static ItemStack readStandardScope(ItemStack gun) {
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
                        applyViewPath(index, attachmentModel, ps, attachmentItem);
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
        return TimelessAPI.getClientSlotAdapterIndex(adapterId)
                .map(com.tacz.guns.client.resource.index.ClientSlotAdapterIndex::getMountOffset)
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
        return TimelessAPI.getClientSlotAdapterIndex(adapterId)
                .map(com.tacz.guns.client.resource.index.ClientSlotAdapterIndex::getMountOffset)
                .orElse(null);
    }

    private static void applyViewPath(ClientAttachmentIndex index, com.tacz.guns.client.model.BedrockAttachmentModel attachmentModel,
                                      PoseStack ps, ItemStack attachmentItem) {
        if (attachmentModel == null) return;
        ps.translate(0f, -1.5f, 0f);
        com.tacz.guns.client.model.bedrock.BedrockPart root = attachmentModel.getRootNode();
        if (root != null) root.translateAndRotateAndScale(ps);
        int[] views = index.getViews();
        int viewIndex = 0;
        if (views != null && views.length > 0 && attachmentItem.getTag() != null) {
            int zoom = com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor.getZoomNumberFromTag(attachmentItem.getTag());
            viewIndex = views[zoom % views.length] - 1;
            if (viewIndex < 0) viewIndex = 0;
        }
        List<com.tacz.guns.client.model.bedrock.BedrockPart> view = attachmentModel.getScopeViewPath(viewIndex);
        if (view == null) return;
        for (com.tacz.guns.client.model.bedrock.BedrockPart part : view) {
            if (part != null) part.translateAndRotateAndScale(ps);
        }
    }
}
