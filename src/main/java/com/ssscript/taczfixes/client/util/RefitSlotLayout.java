package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.client.gui.GunRefitScreen;

/** 改装界面配件槽尺寸布局: 所有槽位/候选列位置与尺寸以此为准。 */
public final class RefitSlotLayout {
    private RefitSlotLayout() {
    }

    public static double scale() {
        return Config.REFIT_SLOT_SIZE.get();
    }

    /** 槽位边长(像素)。 */
    public static int size() {
        return Math.max(4, (int) Math.round(GunRefitScreen.SLOT_SIZE * scale()));
    }

    /** 是否为非默认尺寸。 */
    public static boolean scaled() {
        return size() != GunRefitScreen.SLOT_SIZE;
    }

    /** 最右侧槽位的 x。 */
    public static int firstX(int screenWidth) {
        return screenWidth - 12 - size();
    }

    /** 从右往左第 index 个槽位的 x。 */
    public static int slotX(int screenWidth, int index) {
        return firstX(screenWidth) - index * size();
    }

    /** 标准/自定义槽位尺寸数值缩放。 */
    public static int scaled(int value) {
        return (int) Math.round(value * scale());
    }
}
