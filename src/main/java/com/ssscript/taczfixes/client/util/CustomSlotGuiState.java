package com.ssscript.taczfixes.client.util;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;

public final class CustomSlotGuiState {
    private static String selectedSlot;
    private static int currentPage;
    private static String viewFromSlot;
    private static AttachmentType viewFromType;

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
        viewFromSlot = selectedSlot;
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
}
