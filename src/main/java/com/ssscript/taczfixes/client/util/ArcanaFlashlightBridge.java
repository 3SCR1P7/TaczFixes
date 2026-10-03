package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import group.taczexpands.common.accessor.IAccessorAttachmentData;
import group.taczexpands.dist.DKdo8Awk;
import group.taczexpands.dist.YKThsud9;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * TaCZ: Arcana 战术手电查找桥接。
 * Arcana 只扫描枪械的标准配件槽(AttachmentType)获取手电配置, 且双持时只读取主手枪械:
 * 1. 装在 taczfixes 自定义槽位(TaczFixesCustomSlots)中的手电, 原生查找查不到;
 * 2. 手电装在副手枪械上时, 需要回退到副手枪械的自定义槽位查找, 并配合开关镜像。
 */
public final class ArcanaFlashlightBridge {

    private ArcanaFlashlightBridge() {
    }

    public static DKdo8Awk resolveFlashlight(ItemStack gunStack) {
        DKdo8Awk direct = resolveDirect(gunStack);
        if (direct != null) {
            return direct;
        }
        ItemStack other = otherHandGun(gunStack);
        return other == null ? null : resolveDirect(other);
    }

    /** 对单把枪械: 先原生标准槽查找, 再自定义槽查找。 */
    private static DKdo8Awk resolveDirect(ItemStack gunStack) {
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

    /** 双持时 Arcana 只按主手查询, 手电装在副手(标准槽或自定义槽)时回退到另一只手的枪械。 */
    private static ItemStack otherHandGun(ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty()) {
            return null;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !DualWieldEligibility.isDualWielding(player)) {
            return null;
        }
        ItemStack main = player.getMainHandItem();
        ItemStack offhand = player.getOffhandItem();
        ItemStack other;
        if (gunStack == main || DualWieldStackId.matches(gunStack, main)) {
            other = offhand;
        } else if (gunStack == offhand || DualWieldStackId.matches(gunStack, offhand)) {
            other = main;
        } else {
            return null;
        }
        if (other.isEmpty() || other == gunStack) {
            return null;
        }
        return other;
    }
}
