package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.modifier.custom.AdsModifier;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ScopeSwitchState {
    private static final Map<ResourceLocation, String> ACTIVE = new HashMap<>();

    public static float aimingProgressValue = 0f;

    private ScopeSwitchState() {
    }

    public static String getActiveSlot(ItemStack gun) {
        ResourceLocation gunId = gunId(gun);
        if (gunId == null) return null;
        return ACTIVE.get(gunId);
    }

    public static void cycle(ItemStack gun) {
        ResourceLocation gunId = gunId(gun);
        if (gunId == null) return;
        List<String> candidates = collectCandidates(gun);
        String active = ACTIVE.get(gunId);
        if (active != null && !candidates.contains(active)) {
            active = null;
        }
        String next;
        if (active == null) {
            next = candidates.isEmpty() ? null : candidates.get(0);
        } else {
            int idx = candidates.indexOf(active);
            if (idx + 1 < candidates.size()) {
                next = candidates.get(idx + 1);
            } else {
                next = null;
            }
        }
        if (next == null) {
            ACTIVE.remove(gunId);
        } else {
            ACTIVE.put(gunId, next);
        }
    }

    public static float aimingZoom(IGun iGun, ItemStack gun) {
        String active = getActiveSlot(gun);
        if (active != null) {
            ItemStack scope = CustomSlotStorage.get(gun, active);
            if (!scope.isEmpty()) {
                IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
                ResourceLocation id = attachment == null ? DefaultAssets.EMPTY_ATTACHMENT_ID : attachment.getAttachmentId(scope);
                if (!DefaultAssets.isEmptyAttachmentId(id)) {
                    float[] zooms = TimelessAPI.getClientAttachmentIndex(id).map(ClientAttachmentIndex::getZoom).orElse(null);
                    if (zooms != null) {
                        int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scope.getTag());
                        return zooms[zoomNumber % zooms.length];
                    }
                }
            }
        }
        return iGun.getAimingZoom(gun);
    }

    public static boolean isCustomScopeSlotActive(ItemStack gun) {
        String active = getActiveSlot(gun);
        if (active == null) return false;
        ResourceLocation gunId = gunId(gun);
        if (gunId == null) return false;
        Map<String, CustomSlotDefinition> slots = CustomSlotManager.getSlots(gunId);
        CustomSlotDefinition def = slots.get(active);
        return def != null && !def.isCustom() && "scope".equalsIgnoreCase(def.type);
    }

    public static Optional<Float> customScopeAimTime(ItemStack gun) {
        if (!isCustomScopeSlotActive(gun)) return Optional.empty();
        String active = getActiveSlot(gun);
        ResourceLocation gunId = gunId(gun);
        if (active == null || gunId == null) return Optional.empty();
        ItemStack scope = CustomSlotStorage.get(gun, active);
        if (scope.isEmpty()) return Optional.empty();
        IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
        if (attachment == null) return Optional.empty();
        float base = TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getGunData().getAimTime())
                .orElse(1.0f);
        double result = base;
        ResourceLocation attachmentId = attachment.getAttachmentId(scope);
        AttachmentData data = CommonAssetsManager.getInstance().getAttachmentData(attachmentId);
        if (data == null) {
            data = TimelessAPI.getCommonAttachmentIndex(attachmentId)
                    .map(CommonAttachmentIndex::getData)
                    .orElse(null);
        }
        if (data != null && data.getModifier() != null) {
            JsonProperty<?> property = data.getModifier().get(AdsModifier.ID);
            if (property != null && property.getValue() instanceof Modifier modifier) {
                result = AttachmentPropertyManager.eval(modifier, result);
            }
        }
        return Optional.of((float) Math.max(0, result));
    }

    public static ResourceLocation attachmentId(IGun iGun, ItemStack gun, AttachmentType type) {
        if (type == AttachmentType.SCOPE) {
            String active = getActiveSlot(gun);
            if (active != null) {
                ItemStack scope = CustomSlotStorage.get(gun, active);
                if (!scope.isEmpty()) {
                    IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
                    if (attachment != null) return attachment.getAttachmentId(scope);
                }
            }
        }
        return iGun.getAttachmentId(gun, type);
    }

    public static CompoundTag attachmentTag(IGun iGun, ItemStack gun, AttachmentType type) {
        if (type == AttachmentType.SCOPE) {
            String active = getActiveSlot(gun);
            if (active != null) {
                ItemStack scope = CustomSlotStorage.get(gun, active);
                if (!scope.isEmpty() && scope.getTag() != null) return scope.getTag();
            }
        }
        return iGun.getAttachmentTag(gun, type);
    }

    private static List<String> collectCandidates(ItemStack gun) {
        List<String> list = new ArrayList<>();
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) return list;
        ResourceLocation gunId = igun.getGunId(gun);
        Map<String, CustomSlotDefinition> slots = CustomSlotManager.getSlots(gunId);
        for (Map.Entry<String, CustomSlotDefinition> entry : slots.entrySet()) {
            CustomSlotDefinition def = entry.getValue();
            if (def == null || def.isCustom() || !"scope".equalsIgnoreCase(def.type)) continue;
            if (CustomSlotStorage.get(gun, entry.getKey()).isEmpty()) continue;
            if (!CustomSlotManager.isDependenceMet(gunId, gun, def)) continue;
            list.add(entry.getKey());
        }
        return list;
    }

    private static ResourceLocation gunId(ItemStack gun) {
        IGun igun = IGun.getIGunOrNull(gun);
        return igun == null ? null : igun.getGunId(gun);
    }
}