package com.ssscript.taczfixes.common.data;

import com.google.gson.JsonElement;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashSet;
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
        if (gunId == null) return Collections.emptyMap();
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        GunTaczFixesData data = TaczFixesDataManager.get(dataId);
        return data == null || data.attachment_slots == null
                ? Collections.emptyMap()
                : data.attachment_slots;
    }

    public static CustomSlotDefinition getSlot(ResourceLocation gunId, String slotId) {
        return getSlots(gunId).get(slotId);
    }

    public static boolean matchesSlot(CustomSlotDefinition def, ResourceLocation gunId,
                                      ResourceLocation attachmentId, AttachmentType attachmentType) {
        if (def == null || attachmentId == null) return false;
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

    public static boolean matchesAllow(CustomSlotDefinition def, ResourceLocation attachmentId) {
        for (String allow : def.getAllowAttachments()) {
            if (matchesIdOrTag(attachmentId, allow)) return true;
        }
        return false;
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
            com.ssscript.taczfixes.common.register.TaczFixesMod.LOGGER.warn("taczfixes: tacz tag lookup failed for {}", tagId, e);
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
        if (getSlot(gunId, refId) != null) {
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

    public static boolean hasItem(ResourceLocation gunId, ItemStack gunStack, String refId) {
        return !getItemIn(gunId, gunStack, refId).isEmpty();
    }

    public static Set<ResourceLocation> tagContents(ResourceLocation tagId) {
        Set<ResourceLocation> ids = ALLOW_TAGS.get(tagId);
        return ids == null ? Collections.emptySet() : new HashSet<>(ids);
    }

    public static void cascadeUnloadDependents(net.minecraft.server.level.ServerPlayer player, ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        ResourceLocation gunId = gun.getGunId(gunStack);
        Map<String, CustomSlotDefinition> slots = getSlots(gunId);
        if (slots.isEmpty()) return;
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
        boolean liberated = com.ssscript.taczfixes.common.util.LiberateCompat.isLiberated(player);
        for (Map.Entry<String, ItemStack> e : unloaded.entrySet()) {
            if (liberated) continue;
            if (!player.getInventory().add(e.getValue())) {
                player.drop(e.getValue(), false);
            }
        }
    }

    public static void cascadeUnloadConflicts(net.minecraft.server.level.ServerPlayer player, ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        ResourceLocation gunId = gun.getGunId(gunStack);
        Map<String, CustomSlotDefinition> slots = getSlots(gunId);
        if (slots.isEmpty()) return;
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
        boolean liberated = com.ssscript.taczfixes.common.util.LiberateCompat.isLiberated(player);
        for (Map.Entry<String, ItemStack> e : unloaded.entrySet()) {
            if (liberated) continue;
            if (!player.getInventory().add(e.getValue())) {
                player.drop(e.getValue(), false);
            }
        }
    }
}
