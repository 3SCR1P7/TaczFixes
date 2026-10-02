package com.ssscript.taczfixes.common.data;

import com.google.gson.JsonElement;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.util.VirtualOemAttachment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CustomSlotManager {
    private static final Map<ResourceLocation, Set<ResourceLocation>> ALLOW_TAGS = new ConcurrentHashMap<>();

    private CustomSlotManager() {
    }

    public static void putAllTags(Map<ResourceLocation, Set<ResourceLocation>> tags) {
        ALLOW_TAGS.clear();
        ALLOW_TAGS.putAll(tags);
    }

    public static Map<String, CustomSlotDefinition> getSlots(ResourceLocation gunId) {
        AttachmentSlotsConfig cfg = getConfig(gunId);
        return cfg == null || cfg.slots == null
                ? Collections.emptyMap()
                : cfg.slots;
    }

    /** 获取 attachment_slots 配置块(含两个开关), 未配置返回 null。 */
    public static AttachmentSlotsConfig getConfig(ResourceLocation gunId) {
        if (gunId == null) return null;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        GunTaczFixesData data = TaczFixesDataManager.get(dataId);
        return data == null ? null : data.attachment_slots;
    }

    /** hidden_unavailable: false 时 dependence/conflict 未满足的自定义槽仍显示(用不可用图标)。默认 true(隐藏)。 */
    public static boolean isHiddenUnavailable(ResourceLocation gunId) {
        AttachmentSlotsConfig cfg = getConfig(gunId);
        return cfg == null || cfg.hidden_unavailable == null || cfg.hidden_unavailable;
    }

    /** hidden_unavailable_default: true 时 tacz 默认槽在类型未开放时直接隐藏。枪械 data 未显式配置时跟随配置文件。 */
    public static boolean isHiddenUnavailableDefault(ResourceLocation gunId) {
        AttachmentSlotsConfig cfg = getConfig(gunId);
        if (cfg != null && cfg.hidden_unavailable_default != null) {
            return cfg.hidden_unavailable_default;
        }
        return com.ssscript.taczfixes.common.config.Config.HIDE_UNAVAILABLE_DEFAULT_SLOTS.get();
    }

    public static CustomSlotDefinition getSlot(ResourceLocation gunId, String slotId) {
        return getSlots(gunId).get(slotId);
    }

    /**
     * 合并视图中的槽位: def 为定义, source 为定义该槽的配件(EMPTY 表示枪械 data 自带),
     * mountType/mountSlotId 记录该配件当前的安装位置, modelSlotId 为模型里的定位组 id
     * (槽 id 冲突时会用 "provided@mount" 去重, 但模型节点仍使用原始 id)。
     */
    public record SlotEntry(CustomSlotDefinition def, ItemStack source, AttachmentType mountType,
                            String mountSlotId, String modelSlotId) {
    }

    /** 枪械 data + 已安装配件定义的槽位合并视图(槽 id 冲突时按安装位去重, 配件递归展开并去环)。 */
    public static Map<String, SlotEntry> getEntries(ItemStack gunStack) {
        Map<String, SlotEntry> result = new LinkedHashMap<>();
        if (gunStack == null || gunStack.isEmpty()) return result;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return result;
        for (Map.Entry<String, CustomSlotDefinition> entry : getSlots(gun.getGunId(gunStack)).entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), new SlotEntry(entry.getValue(), ItemStack.EMPTY,
                        AttachmentType.NONE, null, entry.getKey()));
            }
        }
        Deque<Object[]> queue = new ArrayDeque<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ItemStack item = gun.getAttachment(gunStack, type);
            if (!item.isEmpty()) {
                queue.add(new Object[]{item, type, null});
            }
        }
        for (String slotId : new ArrayList<>(result.keySet())) {
            SlotEntry slot = result.get(slotId);
            ItemStack item = CustomSlotStorage.getEffective(gunStack, slotId, slot.def());
            if (!item.isEmpty()) {
                queue.add(new Object[]{item, AttachmentType.NONE, slotId});
            }
        }
        Set<String> processedMounts = new HashSet<>();
        while (!queue.isEmpty()) {
            Object[] queued = queue.poll();
            ItemStack stack = (ItemStack) queued[0];
            AttachmentType mountType = (AttachmentType) queued[1];
            String mountSlotId = (String) queued[2];
            String mountKey = mountType != AttachmentType.NONE
                    ? "t:" + mountType.name() : "s:" + mountSlotId;
            if (!processedMounts.add(mountKey)) continue;
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
            if (attachment == null) continue;
            AttachmentSlotsConfig config = AttachmentTaczFixesManager.getSlotsConfig(stack);
            if (config == null || config.slots == null || config.slots.isEmpty()) continue;
            for (Map.Entry<String, CustomSlotDefinition> slot : config.slots.entrySet()) {
                String provided = slot.getKey();
                if (provided == null || provided.isEmpty() || slot.getValue() == null) continue;
                String effective = uniqueSlotId(result, provided, mountType, mountSlotId);
                result.put(effective, new SlotEntry(slot.getValue(), stack, mountType, mountSlotId, provided));
                ItemStack child = CustomSlotStorage.getEffective(gunStack, effective, slot.getValue());
                if (!child.isEmpty()) {
                    queue.add(new Object[]{child, AttachmentType.NONE, effective});
                }
            }
        }
        return result;
    }

    /** 槽 id 已存在时用 "provided@安装槽" 去重; 模型定位组仍使用 provided。 */
    private static String uniqueSlotId(Map<String, SlotEntry> result, String provided,
                                       AttachmentType mountType, String mountSlotId) {
        if (!result.containsKey(provided)) return provided;
        String mount = mountSlotId != null ? mountSlotId
                : (mountType == null || mountType == AttachmentType.NONE
                ? "x" : mountType.name().toLowerCase(java.util.Locale.ROOT));
        String base = provided + "@" + mount;
        String candidate = base;
        int i = 2;
        while (result.containsKey(candidate)) {
            candidate = base + "#" + i;
            i++;
        }
        return candidate;
    }

    public static Map<String, CustomSlotDefinition> getSlots(ItemStack gunStack) {
        Map<String, CustomSlotDefinition> result = new LinkedHashMap<>();
        for (Map.Entry<String, SlotEntry> entry : getEntries(gunStack).entrySet()) {
            result.put(entry.getKey(), entry.getValue().def());
        }
        return result;
    }

    public static CustomSlotDefinition getSlot(ItemStack gunStack, String slotId) {
        SlotEntry entry = getEntries(gunStack).get(slotId);
        return entry == null ? null : entry.def();
    }

    /** 槽位完整条目(含定义来源); 不存在返回 null。 */
    public static SlotEntry getEntry(ItemStack gunStack, String slotId) {
        return getEntries(gunStack).get(slotId);
    }

    /** 槽定义里的 pos_alter 范围 [min, max]; 未配置返回 null。 */
    public static float[] getPosAlterRange(ItemStack gunStack, String slotId) {
        SlotEntry entry = getEntries(gunStack).get(slotId);
        if (entry == null || entry.def() == null) return null;
        java.util.List<Double> values = entry.def().pos_alter;
        if (values == null || values.size() < 2) return null;
        Double minValue = values.get(0);
        Double maxValue = values.get(1);
        if (minValue == null || maxValue == null
                || !Double.isFinite(minValue.doubleValue()) || !Double.isFinite(maxValue.doubleValue())) {
            return null;
        }
        float min = (float) minValue.doubleValue();
        float max = (float) maxValue.doubleValue();
        return new float[]{Math.min(min, max), Math.max(min, max)};
    }

    public static boolean matchesSlot(CustomSlotDefinition def, ResourceLocation gunId,
                                      ResourceLocation attachmentId, AttachmentType attachmentType) {
        if (def == null || attachmentId == null) return false;
        if (matchesBlacklist(def, attachmentId)) return false;
        if (matchesWhitelist(def, attachmentId)) return true;
        if (def.isCustom()) {
            return matchesAllow(def, attachmentId);
        }
        if (!def.getAllowAttachments().isEmpty()) {
            return matchesAllow(def, attachmentId);
        }
        try {
            AttachmentType defType = AttachmentType.valueOf(def.type.toUpperCase());
            return attachmentType == defType;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** 是否命中槽位黑名单(支持 #tag), 命中则任何方式都无法安装。 */
    public static boolean matchesBlacklist(CustomSlotDefinition def, ResourceLocation attachmentId) {
        if (def == null || attachmentId == null) return false;
        for (String entry : def.getBlacklist()) {
            if (matchesIdOrTag(attachmentId, entry)) return true;
        }
        return false;
    }

    /** 是否命中槽位白名单(支持 #tag), 命中则无视枪械/槽位限制强制可装(黑名单除外)。 */
    public static boolean matchesWhitelist(CustomSlotDefinition def, ResourceLocation attachmentId) {
        if (def == null || attachmentId == null) return false;
        for (String entry : def.getWhitelist()) {
            if (matchesIdOrTag(attachmentId, entry)) return true;
        }
        return false;
    }

    public static boolean matchesAllow(CustomSlotDefinition def, ResourceLocation attachmentId) {
        for (String allow : def.getAllowAttachments()) {
            if (matchesIdOrTag(attachmentId, allow)) return true;
        }
        return false;
    }

    /** attachmentId 是否在该自定义槽的 builtin_attachments.attachments 原厂候选列表内。 */
    public static boolean isBuiltinCandidate(CustomSlotDefinition def, ResourceLocation attachmentId) {
        if (def == null || attachmentId == null) return false;
        if (matchesBlacklist(def, attachmentId)) return false;
        for (String entry : def.getBuiltinAttachmentIds()) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (attachmentId.equals(id)) return true;
        }
        return false;
    }

    /** 该槽默认预装的原厂件 id(default_attached), 未配置或命中黑名单返回 null。 */
    public static ResourceLocation getBuiltinDefault(CustomSlotDefinition def) {
        if (def == null || def.builtin_attachments == null) return null;
        String id = def.builtin_attachments.default_attached;
        if (id == null || id.isEmpty()) return null;
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed != null && matchesBlacklist(def, parsed)) return null;
        return parsed;
    }

    /** 构建原厂件(虚拟 OEM): 与 tacz 的虚拟原厂件一致, 带标记, 卸下不会返还。 */
    public static ItemStack buildBuiltinItem(ResourceLocation attachmentId) {
        if (attachmentId == null) return ItemStack.EMPTY;
        ItemStack item = AttachmentItemBuilder.create().setId(attachmentId).build();
        if (!item.isEmpty()) {
            VirtualOemAttachment.mark(item);
            com.ssscript.taczfixes.common.compat.ArcanaSkillBridge.markGenerated(item);
        }
        return item;
    }

    public static boolean matchesIdOrTag(ResourceLocation attachmentId, String entry) {
        if (entry == null || entry.isEmpty()) return false;
        if (entry.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(entry.substring(1));
            if (tagId == null) return false;
            Set<ResourceLocation> ids = ALLOW_TAGS.get(tagId);
            if (ids == null || ids.isEmpty()) {
                ids = taczAllowTagContents(tagId);
            }
            return ids != null && ids.contains(attachmentId);
        }
        ResourceLocation id = ResourceLocation.tryParse(entry);
        return id != null && id.equals(attachmentId);
    }

    private static Set<ResourceLocation> taczAllowTagContents(ResourceLocation tagId) {
        try {
            java.util.Set<String> ids = CommonAssetsManager.getInstance()
                    .getAttachmentTags(tagId);
            if (ids == null || ids.isEmpty()) return null;
            Set<ResourceLocation> result = new HashSet<>();
            for (String id : ids) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) result.add(rl);
            }
            return result;
        } catch (Exception e) {
            com.ssscript.taczfixes.TaczFixesMod.LOGGER.warn("taczfixes: tacz tag lookup failed for {}", tagId, e);
            return null;
        }
    }

    public static boolean isDependenceMet(ResourceLocation gunId, ItemStack gunStack, CustomSlotDefinition def) {
        for (Map.Entry<String, JsonElement> entry : def.getDependence().entrySet()) {
            if (!satisfies(gunId, gunStack, entry.getKey(), entry.getValue())) return false;
        }
        return true;
    }

    public static boolean isConflictOccupied(ResourceLocation gunId, ItemStack gunStack, CustomSlotDefinition def) {
        for (Map.Entry<String, JsonElement> entry : def.getConflict().entrySet()) {
            if (satisfies(gunId, gunStack, entry.getKey(), entry.getValue())) return true;
        }
        return false;
    }

    public static boolean satisfies(ResourceLocation gunId, ItemStack gunStack, String refId, JsonElement cond) {
        ItemStack item = getItemIn(gunId, gunStack, refId);
        if (item.isEmpty()) return false;
        if (cond == null || cond.isJsonNull()) return true;
        if (cond.isJsonPrimitive() && cond.getAsJsonPrimitive().isBoolean()) {
            return cond.getAsBoolean();
        }
        if (!cond.isJsonArray()) return false;
        IAttachment ia = IAttachment.getIAttachmentOrNull(item);
        if (ia == null) return false;
        ResourceLocation attachmentId = ia.getAttachmentId(item);
        for (JsonElement e : cond.getAsJsonArray()) {
            if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) continue;
            if (matchesIdOrTag(attachmentId, e.getAsString())) return true;
        }
        return false;
    }

    public static ItemStack getItemIn(ResourceLocation gunId, ItemStack gunStack, String refId) {
        if (getSlot(gunStack, refId) != null) {
            return CustomSlotStorage.get(gunStack, refId);
        }
        try {
            AttachmentType type = AttachmentType.valueOf(refId.toUpperCase());
            IGun gun = IGun.getIGunOrNull(gunStack);
            if (gun == null) return ItemStack.EMPTY;
            return gun.getAttachment(gunStack, type);
        } catch (IllegalArgumentException ex) {
            return ItemStack.EMPTY;
        }
    }

    public static void cascadeUnloadDependents(net.minecraft.server.level.ServerPlayer player, ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        ResourceLocation gunId = gun.getGunId(gunStack);
        Map<String, CustomSlotDefinition> slots = getSlots(gunStack);
        if (slots.isEmpty()) {
            cascadeUnloadOrphans(player, gunStack);
            return;
        }
        java.util.Map<String, ItemStack> unloaded = new java.util.LinkedHashMap<>();
        boolean changed;
        int guard = 0;
        do {
            changed = false;
            for (Map.Entry<String, CustomSlotDefinition> entry : slots.entrySet()) {
                if (unloaded.containsKey(entry.getKey())) continue;
                if (CustomSlotStorage.get(gunStack, entry.getKey()).isEmpty()) continue;
                if (!isDependenceMet(gunId, gunStack, entry.getValue())) {
                    ItemStack removed = CustomSlotStorage.unload(gunStack, entry.getKey());
                    if (!removed.isEmpty()) {
                        unloaded.put(entry.getKey(), removed);
                        changed = true;
                    }
                }
            }
            guard++;
        } while (changed && guard < 64);
        cascadeUnloadOrphans(player, gunStack);
        boolean virtual = com.ssscript.taczfixes.common.util.VirtualAttachments.isActive(player);
        for (Map.Entry<String, ItemStack> e : unloaded.entrySet()) {
            if (virtual || VirtualOemAttachment.isMarked(e.getValue())) continue;
            if (!player.getInventory().add(e.getValue())) {
                player.drop(e.getValue(), false);
            }
        }
    }

    /**
     * 卸载来源配件移除后遗留的孤儿槽: 虚拟配件模式/builtin 直接消失(不留在 NBT),
     * 物理配件返还背包(满则掉落)。适配器记录一并清理。
     */
    public static void cascadeUnloadOrphans(net.minecraft.server.level.ServerPlayer player, ItemStack gunStack) {
        if (player == null || gunStack == null || gunStack.isEmpty()) return;
        java.util.Set<String> stored = new java.util.LinkedHashSet<>();
        net.minecraft.nbt.CompoundTag tag = gunStack.getTag();
        if (tag != null) {
            collectStoredKeys(tag, CustomSlotStorage.TAG_KEY, stored);
            collectStoredKeys(tag, CustomSlotStorage.BUILTIN_TAG_KEY, stored);
            collectStoredKeys(tag, CustomSlotStorage.ADAPTER_TAG_KEY, stored);
        }
        if (stored.isEmpty()) return;
        java.util.Set<String> known = getEntries(gunStack).keySet();
        boolean virtual = com.ssscript.taczfixes.common.util.VirtualAttachments.isActive(player);
        for (String slotId : stored) {
            if (known.contains(slotId)) continue;
            ItemStack removed = CustomSlotStorage.unload(gunStack, slotId);
            CustomSlotStorage.setAdapter(gunStack, slotId, null);
            if (removed.isEmpty()) continue;
            if (virtual || VirtualOemAttachment.isMarked(removed)) continue;
            if (!player.getInventory().add(removed)) {
                player.drop(removed, false);
            }
        }
    }

    private static void collectStoredKeys(net.minecraft.nbt.CompoundTag tag, String key, Set<String> out) {
        if (tag.contains(key, 10)) {
            out.addAll(tag.getCompound(key).getAllKeys());
        }
    }

    public static void cascadeUnloadConflicts(net.minecraft.server.level.ServerPlayer player, ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        ResourceLocation gunId = gun.getGunId(gunStack);
        Map<String, CustomSlotDefinition> slots = getSlots(gunStack);
        if (slots.isEmpty()) {
            cascadeUnloadOrphans(player, gunStack);
            return;
        }
        java.util.Map<String, ItemStack> unloaded = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, CustomSlotDefinition> entry : slots.entrySet()) {
            if (unloaded.containsKey(entry.getKey())) continue;
            ItemStack stored = CustomSlotStorage.get(gunStack, entry.getKey());
            if (stored.isEmpty()) continue;
            boolean conflict = isConflictOccupied(gunId, gunStack, entry.getValue());
            if (conflict) {
                ItemStack removed = CustomSlotStorage.unload(gunStack, entry.getKey());
                if (!removed.isEmpty()) {
                    unloaded.put(entry.getKey(), removed);
                }
            }
        }
        boolean virtual = com.ssscript.taczfixes.common.util.VirtualAttachments.isActive(player);
        for (Map.Entry<String, ItemStack> e : unloaded.entrySet()) {
            if (virtual || VirtualOemAttachment.isMarked(e.getValue())) continue;
            if (!player.getInventory().add(e.getValue())) {
                player.drop(e.getValue(), false);
            }
        }
        cascadeUnloadOrphans(player, gunStack);
    }
}
