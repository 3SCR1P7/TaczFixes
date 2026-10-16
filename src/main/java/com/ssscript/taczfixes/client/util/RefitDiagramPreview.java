package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * 改装界面: 鼠标悬浮候选配件时, 用"卸下该槽原配件 + 安装此配件"的枪械数值替换显示图表所用的属性缓存。
 * 悬浮矩形由候选槽控件每帧记录, 鼠标移出即失效。
 */
public final class RefitDiagramPreview {
    private static String slotKey;
    private static boolean custom;
    private static ItemStack attachment = ItemStack.EMPTY;
    private static int x;
    private static int y;
    private static int width;
    private static int height;
    private static AttachmentCacheProperty cache;
    private static int cacheHash;

    private RefitDiagramPreview() {
    }

    public static void setStandard(AttachmentType type, ItemStack item, int x, int y, int width, int height) {
        if (type == null || type == AttachmentType.NONE || item == null || item.isEmpty()) return;
        slotKey = type.name().toLowerCase(Locale.ROOT);
        custom = false;
        attachment = item;
        setRect(x, y, width, height);
    }

    public static void setCustom(String slotId, ItemStack item, int x, int y, int width, int height) {
        if (slotId == null || item == null || item.isEmpty()) return;
        slotKey = slotId;
        custom = true;
        attachment = item;
        setRect(x, y, width, height);
    }

    private static void setRect(int rectX, int rectY, int rectWidth, int rectHeight) {
        x = rectX;
        y = rectY;
        width = rectWidth;
        height = rectHeight;
    }

    public static void clear() {
        slotKey = null;
        attachment = ItemStack.EMPTY;
        cache = null;
        cacheHash = 0;
    }

    /** 悬浮中的预览属性缓存; 无悬浮/鼠标已移开返回 null(调用方回退真实缓存)。 */
    public static AttachmentCacheProperty previewCache() {
        if (slotKey == null || attachment.isEmpty()) return null;
        if (!isMouseOver()) {
            clear();
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        ItemStack gun = mc.player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) return null;
        int hash = 31 * attachment.hashCode() + slotKey.hashCode();
        if (cache == null || hash != cacheHash) {
            GunData gunData = TimelessAPI.getCommonGunIndex(igun.getGunId(gun))
                    .map(CommonGunIndex::getGunData).orElse(null);
            if (gunData == null) return null;
            cache = build(gun, gunData);
            cacheHash = hash;
        }
        return cache;
    }

    private static boolean isMouseOver() {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        if (window.getScreenWidth() <= 0 || window.getScreenHeight() <= 0) return false;
        double mouseX = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static AttachmentCacheProperty build(ItemStack gun, GunData gunData) {
        try {
            ItemStack preview = gun.copy();
            IGun igun = IGun.getIGunOrNull(preview);
            if (igun == null) return null;
            if (custom) {
                CustomSlotStorage.install(preview, slotKey, attachment.copy());
            } else {
                AttachmentType type = AttachmentType.valueOf(slotKey.toUpperCase(Locale.ROOT));
                igun.unloadAttachment(preview, type);
                igun.installAttachment(preview, attachment.copy());
            }
            AttachmentCacheProperty property = new AttachmentCacheProperty();
            property.eval(preview, gunData);
            return property;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
