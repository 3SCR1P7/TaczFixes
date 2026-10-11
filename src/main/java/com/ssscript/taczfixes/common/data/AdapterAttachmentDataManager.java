package com.ssscript.taczfixes.common.data;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.util.SlotAdapterHelper;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 槽位适配器(data/slot_adapters)的 data 按配件原生格式(AttachmentData, 含 modifier)解析并缓存,
 * 使适配器能像配件一样提供增益(含 damage/armor_ignore 等原生修饰符, 并计入改装界面柱状图)。
 * 仅当某个槽位“选择了适配器”且“该槽位装了配件”时才应用其加成。
 */
public final class AdapterAttachmentDataManager {
    private static final Map<ResourceLocation, AttachmentData> DATA = new ConcurrentHashMap<>();

    private AdapterAttachmentDataManager() {
    }

    public static void clear() {
        DATA.clear();
    }

    public static void putAll(Map<ResourceLocation, AttachmentData> map) {
        for (Map.Entry<ResourceLocation, AttachmentData> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                DATA.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Nullable
    public static AttachmentData get(@Nullable ResourceLocation dataId) {
        return dataId == null ? null : DATA.get(dataId);
    }

    /** 遍历枪械上已安装适配器(标准槽 + 自定义槽)的原生 data; 槽位为空则跳过该适配器。 */
    public static void forEachAdapterData(ItemStack gunItem, Consumer<AttachmentData> consumer) {
        if (gunItem == null || gunItem.isEmpty()) {
            return;
        }
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) {
            return;
        }
        Set<ResourceLocation> visited = new HashSet<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            // 该槽位装了配件才应用其适配器加成
            if (DefaultAssets.isEmptyAttachmentId(gun.getAttachmentId(gunItem, type))) continue;
            acceptAdapter(SlotAdapterHelper.getSlotAdapter(gunItem, type), visited, consumer);
        }
        for (String slotId : CustomSlotManager.getSlots(gunItem).keySet()) {
            // 该槽位装了配件才应用其适配器加成
            if (CustomSlotStorage.get(gunItem, slotId).isEmpty()) continue;
            acceptAdapter(CustomSlotStorage.getAdapter(gunItem, slotId), visited, consumer);
        }
    }

    /** 收集枪械上已安装适配器的指定修饰符 id 的修饰符列表。 */
    public static List<Modifier> collectModifiers(ItemStack gunItem, String id) {
        List<Modifier> modifiers = new ArrayList<>();
        forEachAdapterData(gunItem, data -> {
            var property = data.getModifier().get(id);
            if (property != null && property.getValue() instanceof Modifier modifier) {
                modifiers.add(modifier);
            }
        });
        return modifiers;
    }

    private static void acceptAdapter(@Nullable ResourceLocation adapterId, Set<ResourceLocation> visited,
                                      Consumer<AttachmentData> consumer) {
        if (adapterId == null) return;
        ResourceLocation dataId = TimelessAPI.getCommonSlotAdapterIndex(adapterId)
                .map(index -> index.getPojo().getData())
                .orElse(null);
        if (dataId == null || !visited.add(dataId)) return;
        AttachmentData data = get(dataId);
        if (data != null) {
            consumer.accept(data);
        }
    }
}
