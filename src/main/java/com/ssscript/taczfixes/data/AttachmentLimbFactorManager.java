package com.ssscript.taczfixes.data;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AttachmentLimbFactorManager {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes");
    private static final Map<ResourceLocation, Modifier> LIMB_FACTORS = new ConcurrentHashMap<>();

    private AttachmentLimbFactorManager() {
    }

    public static void putAll(Map<ResourceLocation, Modifier> map) {
        LIMB_FACTORS.clear();
        LIMB_FACTORS.putAll(map);
    }

    public static Modifier get(ResourceLocation dataId) {
        return dataId == null ? null : LIMB_FACTORS.get(dataId);
    }

    public static double applyLimbFactor(ItemStack gunItem, double baseLimbFactor) {
        List<Modifier> modifiers = collectModifiers(gunItem);
        if (modifiers.isEmpty()) {
            return baseLimbFactor;
        }
        double result = AttachmentPropertyManager.eval(modifiers, baseLimbFactor);
        return result;
    }

    private static List<Modifier> collectModifiers(ItemStack gunItem) {
        List<Modifier> result = new ArrayList<>();
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return result;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation id = gun.getAttachmentId(gunItem, type);
            if (DefaultAssets.isEmptyAttachmentId(id)) continue;
            Modifier modifier = resolveModifier(id);
            if (modifier != null) {
                result.add(modifier);
            }
        }
        return result;
    }

    private static Modifier resolveModifier(ResourceLocation attachmentId) {
        Modifier modifier = get(attachmentId);
        if (modifier != null) return modifier;
        ResourceLocation dataId = TimelessAPI.getCommonAttachmentIndex(attachmentId)
                .map(index -> index.getPojo().getData())
                .orElse(attachmentId);
        return get(dataId);
    }
}
