package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.pojo.data.gun.BuiltInAttachment;
import com.tacz.guns.util.SlotAdapterHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 虚拟配件模式: 配件不消耗、不返还, 候选栏显示所有可用配件。 */
public final class VirtualAttachments {
    private VirtualAttachments() {
    }

    public static boolean isActive(@Nullable Player player) {
        return switch (Config.VIRTUAL_ATTACHMENTS.get()) {
            case TRUE -> true;
            case CREATIVE -> player != null && player.isCreative();
            case FALSE -> false;
        };
    }

    /** 该槽位适配器是否允许此配件(镜像原版 SlotAdapterHelper 语义)。 */
    public static boolean allowsForSlot(ItemStack gunStack, String slotId, ResourceLocation attachmentId) {
        if (attachmentId == null) return false;
        ResourceLocation adapterId = CustomSlotStorage.getAdapter(gunStack, slotId);
        if (adapterId == null) {
            return SlotAdapterHelper.allowsDirectAttachment(gunStack, attachmentId);
        }
        return TimelessAPI.getCommonSlotAdapterIndex(adapterId)
                .map(index -> index.allowsAttachment(attachmentId))
                .orElse(false);
    }

    /** 标准槽虚拟候选: 原厂候选(即使 hidden)+ 该类型下所有允许安装的配件。 */
    public static List<ResourceLocation> availableForStandard(ItemStack gunStack, IGun gun, AttachmentType type) {
        List<ResourceLocation> out = new ArrayList<>();
        if (gun == null || type == null || type == AttachmentType.NONE) return out;
        if (!gun.allowAttachmentType(gunStack, type)) return out;
        // 原厂候选优先展示
        for (ResourceLocation id : builtinIdsForStandard(gunStack, gun, type)) {
            ItemStack item = CustomSlotManager.buildBuiltinItem(id);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
            if (attachment == null || attachment.getType(item) != type) continue;
            if (!gun.allowAttachment(gunStack, item)) continue;
            if (!out.contains(id)) out.add(id);
        }
        for (ResourceLocation id : sortedIds(type)) {
            if (out.contains(id)) continue;
            ItemStack item = CustomSlotManager.buildBuiltinItem(id);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
            if (attachment == null || attachment.getType(item) != type) continue;
            if (!gun.allowAttachment(gunStack, item)) continue;
            out.add(id);
        }
        return out;
    }

    /** 该枪指定标准槽类型的原厂候选(attachments + default_attached + display), 不受 hidden 影响。 */
    public static List<ResourceLocation> builtinIdsForStandard(ItemStack gunStack, IGun gun, AttachmentType type) {
        List<ResourceLocation> out = new ArrayList<>();
        if (gun == null || type == null) return out;
        ResourceLocation gunId = gun.getGunId(gunStack);
        try {
            TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
                BuiltInAttachment builtin = index.getGunData().getBuiltInAttachments().get(type);
                if (builtin == null) return;
                if (builtin.getAttachments() != null) {
                    for (ResourceLocation id : builtin.getAttachments()) {
                        if (id != null && !out.contains(id)) out.add(id);
                    }
                }
                if (builtin.getDefaultAttachment() != null && !out.contains(builtin.getDefaultAttachment())) {
                    out.add(builtin.getDefaultAttachment());
                }
                if (builtin.getDisplayAttachment() != null && !out.contains(builtin.getDisplayAttachment())) {
                    out.add(builtin.getDisplayAttachment());
                }
            });
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 自定义槽虚拟候选: 所有满足槽位匹配与适配器的配件。 */
    public static List<ResourceLocation> availableForCustom(ItemStack gunStack, IGun gun, String slotId,
                                                            CustomSlotDefinition def) {
        List<ResourceLocation> out = new ArrayList<>();
        if (gun == null || def == null) return out;
        ResourceLocation gunId = gun.getGunId(gunStack);
        for (ResourceLocation id : sortedIds(null)) {
            ItemStack item = CustomSlotManager.buildBuiltinItem(id);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
            if (attachment == null) continue;
            if (!CustomSlotManager.matchesSlot(def, gunId, id, attachment.getType(item))) continue;
            if (!allowsForSlot(gunStack, slotId, id)) continue;
            out.add(id);
        }
        return out;
    }

    /** 按 type 过滤(为 null 则全部)并排除 hidden, 按 tacz 排序值排序。 */
    private static List<ResourceLocation> sortedIds(@Nullable AttachmentType type) {
        List<Map.Entry<ResourceLocation, CommonAttachmentIndex>> entries;
        try {
            entries = new ArrayList<>(CommonAssetsManager.getInstance().getAllAttachments());
        } catch (Exception e) {
            return List.of();
        }
        entries.removeIf(entry -> entry.getValue() == null
                || entry.getValue().getPojo() == null
                || entry.getValue().getPojo().isHidden()
                || (type != null && entry.getValue().getType() != type));
        entries.sort(Comparator.comparingInt((Map.Entry<ResourceLocation, CommonAttachmentIndex> entry)
                        -> entry.getValue().getSort())
                .thenComparing(entry -> entry.getKey().toString()));
        List<ResourceLocation> out = new ArrayList<>(entries.size());
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : entries) {
            out.add(entry.getKey());
        }
        return out;
    }
}
