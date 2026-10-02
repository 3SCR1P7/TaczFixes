package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import group.taczexpands.common.accessor.IAccessorAttachmentData;
import group.taczexpands.dist.DKdo8Awk;
import group.taczexpands.dist.YKThsud9;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * TaCZ: Arcana 战术手电查找桥接。
 * Arcana 只扫描枪械的标准配件槽(AttachmentType)获取手电配置,
 * 装在 taczfixes 自定义槽位(TaczFixesCustomSlots)中的手电因此查不到, 开关无效果。
 * 此处在原生查找失败后, 再扫描自定义槽位中的生效配件。
 */
public final class ArcanaFlashlightBridge {

    private ArcanaFlashlightBridge() {
    }

    public static DKdo8Awk resolveFlashlight(ItemStack gunStack) {
        DKdo8Awk base = IAccessorAttachmentData.UyDOzO7w(gunStack);
        if (base != null) {
            return base;
        }
        return findCustomSlotFlashlight(gunStack);
    }

    private static DKdo8Awk findCustomSlotFlashlight(ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) {
            return null;
        }
        try {
            for (Map.Entry<String, CustomSlotDefinition> entry : CustomSlotManager.getSlots(gunStack).entrySet()) {
                ItemStack item = CustomSlotStorage.get(gunStack, entry.getKey());
                if (item.isEmpty()) {
                    continue;
                }
                IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
                if (attachment == null) {
                    continue;
                }
                ResourceLocation id = attachment.getAttachmentId(item);
                if (id == null || DefaultAssets.isEmptyAttachmentId(id)) {
                    continue;
                }
                YKThsud9 holder = TimelessAPI.getCommonAttachmentIndex(id)
                        .map(index -> IAccessorAttachmentData.RdZw8JA8(index.getData()))
                        .orElse(null);
                if (holder != null && holder.NtLdWtKw != null && holder.NtLdWtKw.gDxhhZ8S) {
                    return holder.NtLdWtKw;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
