package com.ssscript.taczfixes.client.util;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;

public final class CustomSlotGuiState {
    private static String selectedSlot;
    private static int currentPage;
    private static String viewFromSlot;
    private static AttachmentType viewFromType;
    private static boolean pendingViewReset;

    private CustomSlotGuiState() {
    }

    public static String get() {
        return selectedSlot;
    }

    public static void set(String slotId) {
        selectedSlot = slotId;
        currentPage = 0;
    }

    public static int getPage() {
        return currentPage;
    }

    public static void setPage(int page) {
        currentPage = page;
    }

    public static void reset() {
        selectedSlot = null;
        currentPage = 0;
    }

    public static void beginRefitViewTransition() {
        // 取消选中的流程会先 reset 再调用 changeRefitScreenView,
        // 此时 selectedSlot 已为 null, 不能覆盖先前显式记录的来源槽位。
        if (selectedSlot != null) {
            viewFromSlot = selectedSlot;
        }
        viewFromType = RefitTransform.getCurrentTransformType();
    }

    public static String getViewFromSlot() {
        return viewFromSlot;
    }

    public static AttachmentType getViewFromType() {
        return viewFromType;
    }

    public static void clearViewTransition() {
        viewFromSlot = null;
        viewFromType = null;
    }

    /** 取消选中时 refit 视图过渡尚未结束, 需延迟切回 NONE。 */
    public static boolean hasPendingViewReset() {
        return pendingViewReset;
    }

    public static void setPendingViewReset(boolean pending) {
        pendingViewReset = pending;
    }
}
