package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.AttachmentGroupOffsetHelper;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 配件定义的自定义槽位挂载链: 来源配件安装处(标准槽/自定义槽, 可递归) + 配件模型内的 *_pos。
 * 每一步复刻来源配件的实际渲染变换: 定位组路径、-1.5Y、槽适配器偏移、pos_alter 与 group_offset。
 */
public final class CustomSlotMount {

    /** 一步挂载: node 为该模型内的定位组; typeKey 为来源配件的附件类型(小写, 用于 group_offset/pos_alter)。 */
    public record Step(BedrockPart node, boolean customSlot, String slotId, ItemStack mountItem,
                       String typeKey, boolean attachmentNode) {
    }

    private static final int MAX_DEPTH = 12;

    private CustomSlotMount() {
    }

    /** 槽位内容渲染前的挂载链(来源配件的安装处)。枪械自带槽返回空。 */
    public static List<Step> sourceChain(BedrockModel gunModel, ItemStack gunStack, CustomSlotManager.SlotEntry entry) {
        return sourceChain(gunModel, gunStack, entry, new HashSet<>(), 0);
    }

    private static List<Step> sourceChain(BedrockModel gunModel, ItemStack gunStack, CustomSlotManager.SlotEntry entry,
                                          Set<String> visitedSlots, int depth) {
        if (entry == null || entry.source().isEmpty() || depth > MAX_DEPTH) return List.of();
        if (entry.mountSlotId() != null) {
            if (!visitedSlots.add(entry.mountSlotId())) return List.of();
            CustomSlotManager.SlotEntry mountEntry = CustomSlotManager.getEntry(gunStack, entry.mountSlotId());
            if (mountEntry == null) return List.of();
            List<Step> chain = new ArrayList<>(sourceChain(gunModel, gunStack, mountEntry, visitedSlots, depth + 1));
            BedrockPart node = nodeFor(gunModel, mountEntry, entry.mountSlotId());
            if (node != null) {
                ItemStack mounted = CustomSlotStorage.get(gunStack, entry.mountSlotId());
                chain.add(new Step(node, true, entry.mountSlotId(), mounted, typeKeyOf(mounted),
                        !mountEntry.source().isEmpty()));
            }
            return chain;
        }
        AttachmentType mountType = entry.mountType();
        if (mountType != null && mountType != AttachmentType.NONE) {
            BedrockPart node = gunModel.getNode(mountType.name().toLowerCase(Locale.ROOT) + "_pos");
            return node == null ? List.of()
                    : List.of(new Step(node, false, null, ItemStack.EMPTY,
                    mountType.name().toLowerCase(Locale.ROOT), false));
        }
        return List.of();
    }

    /** 槽位定义对应的定位组: 枪械模型或来源配件模型中的 slotId_pos。 */
    public static BedrockPart nodeFor(BedrockModel gunModel, CustomSlotManager.SlotEntry entry, String slotId) {
        if (entry == null) return null;
        String modelSlotId = entry.modelSlotId() != null && !entry.modelSlotId().isEmpty()
                ? entry.modelSlotId() : slotId;
        if (entry.source().isEmpty()) {
            return gunModel.getNode(modelSlotId + "_pos");
        }
        BedrockAttachmentModel model = attachmentModel(entry.source());
        return model == null ? null : model.getNode(modelSlotId + "_pos");
    }

    public static void apply(PoseStack pose, List<Step> chain, ItemStack gun,
                             ItemDisplayContext context, int light, int overlay) {
        applyTransforms(pose, chain, gun);
    }

    /** 应用节点链与各类偏移(与来源配件渲染一致), 用于渲染与坐标计算。 */
    public static void applyTransforms(PoseStack pose, List<Step> chain, ItemStack gun) {
        if (chain == null || chain.isEmpty()) return;
        Set<String> appliedTypeKeys = new HashSet<>();
        for (Step step : chain) {
            applyStep(pose, step, gun, appliedTypeKeys);
        }
    }

    private static void applyStep(PoseStack pose, Step step, ItemStack gun, Set<String> appliedTypeKeys) {
        if (step.node() == null) return;
        if (step.attachmentNode()) {
            // 与子配件渲染一致: 配件模型内的定位组需要补 1.5 格(在节点自身变换前)
            pose.translate(0.0F, -1.5F, 0.0F);
        }
        StandbySlotBuffer.applyNodePathTransform(step.node(), pose);
        if (step.customSlot()) {
            applyPosAlter(pose, gun, step.slotId());
            applyAdapterOffset(pose, step.mountItem(), gun, step.slotId());
        }
        // 类型键的偏移在一条挂载链里只应用一次, 避免逐层叠加; 子层级只需跟随上一层
        if (step.typeKey() != null && appliedTypeKeys.add(step.typeKey())) {
            AttachmentGroupOffsetHelper.applyForGun(pose, gun, step.typeKey());
            applyPosAlter(pose, gun, step.typeKey());
        }
    }

    private static void applyPosAlter(PoseStack pose, ItemStack gun, String key) {
        if (key == null || gun == null || gun.isEmpty()) return;
        float z = PosAlterStorage.get(gun, key);
        if (z != 0.0F) {
            pose.translate(0.0F, 0.0F, z / 16.0F);
        }
    }

    private static void applyAdapterOffset(PoseStack pose, ItemStack mountItem, ItemStack gun, String slotId) {
        if (slotId == null || mountItem == null || mountItem.isEmpty()) return;
        IAttachment ia = IAttachment.getIAttachmentOrNull(mountItem);
        if (ia == null) return;
        ResourceLocation attachmentId = ia.getAttachmentId(mountItem);
        ResourceLocation adapterId = CustomSlotStorage.getAdapter(gun, slotId);
        if (adapterId == null) return;
        if (!TimelessAPI.getCommonSlotAdapterIndex(adapterId)
                .map(index -> index.allowsAttachment(attachmentId)).orElse(false)) {
            return;
        }
        TimelessAPI.getClientSlotAdapterIndex(adapterId).ifPresent(index -> {
            org.joml.Vector3f offset = index.getMountOffset();
            if (offset != null) {
                pose.translate(offset.x / 16.0F, -offset.y / 16.0F, offset.z / 16.0F);
            }
        });
    }

    private static String typeKeyOf(ItemStack attachmentItem) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (attachment == null) return null;
        AttachmentType type = attachment.getType(attachmentItem);
        return type == null || type == AttachmentType.NONE ? null : type.name().toLowerCase(Locale.ROOT);
    }

    private static BedrockAttachmentModel attachmentModel(ItemStack attachmentItem) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (attachment == null) return null;
        ResourceLocation id = attachment.getAttachmentId(attachmentItem);
        if (id == null) return null;
        return TimelessAPI.getClientAttachmentIndex(id)
                .map(index -> index.getAttachmentModel())
                .orElse(null);
    }
}
