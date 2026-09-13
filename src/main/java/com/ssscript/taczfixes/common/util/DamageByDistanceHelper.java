package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.modifier.custom.EffectiveRangeModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DamageByDistanceHelper {
    private DamageByDistanceHelper() {
    }

    /**
     * 有效射程 = 枪械 data 的 effective_range，再以当前值作为基准求价全部配件的
     * effective_range(TACZ 原生字段)修饰符。无配件时返回原值。
     */
    public static double resolveEffectiveRange(ItemStack gunItem, ResourceLocation gunId, Double gunEffectiveRange) {
        double result = gunEffectiveRange != null ? gunEffectiveRange : 0.0;
        if (gunItem == null || gunItem.isEmpty() || gunId == null) return result;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null || !gunId.equals(gun.getGunId(gunItem))) return result;
        List<Modifier> modifiers = new ArrayList<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation attachmentId = gun.getAttachmentId(gunItem, type);
            if (DefaultAssets.isEmptyAttachmentId(attachmentId)) continue;
            try {
                com.tacz.guns.resource.pojo.data.attachment.AttachmentData attachmentData =
                        TimelessAPI.getCommonAttachmentIndex(attachmentId)
                                .map(index -> index.getData()).orElse(null);
                if (attachmentData == null || attachmentData.getModifier() == null) continue;
                JsonProperty<?> property = attachmentData.getModifier().get(EffectiveRangeModifier.ID);
                if (property != null && property.getValue() instanceof Modifier modifier) {
                    modifiers.add(modifier);
                }
            } catch (Exception ignored) {
            }
        }
        if (!modifiers.isEmpty()) {
            result = AttachmentPropertyManager.eval(modifiers, result);
        }
        return result;
    }
}
