package com.ssscript.taczfixes.common.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.TaczFixesMod;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class CharmsOffhandRenderCompat {
    private static final String RENDER_CONTEXT_CLASS = "com.VvvV.taczcharms.client.render.CharmRenderContext";
    private static final String RENDERER_CLASS = "com.VvvV.taczcharms.client.render.CharmRenderer";
    private static final String ANCHOR_SELECTION_CLASS = "com.VvvV.taczcharms.client.gui.CharmAnchorSelection";
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();
    private static boolean initialized;
    private static Method beginContext;
    private static Method endContext;
    private static Method renderCaptured;
    private static Method beginAnchorFrame;
    private static Method endAnchorFrame;

    private CharmsOffhandRenderCompat() {
    }

    public static boolean beginFrame(ItemStack stack, ItemDisplayContext displayContext, MultiBufferSource buffer, int light, int overlay, float partialTick) {
        if (!ensureInitialized() || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            beginContext.invoke(null, stack, displayContext, buffer, Integer.valueOf(light), Integer.valueOf(overlay), Float.valueOf(partialTick), Boolean.TRUE);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            logWarning(exception);
            return false;
        }
        try {
            beginAnchorFrame.invoke(null, Boolean.FALSE);
            return true;
        } catch (IllegalAccessException | InvocationTargetException exception2) {
            logWarning(exception2);
            endFrame();
            return false;
        }
    }

    public static void renderCaptured(PoseStack poseStack) {
        if (!initialized || poseStack == null) {
            return;
        }
        try {
            renderCaptured.invoke(null, poseStack);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            logWarning(exception);
        }
    }

    public static void endFrame() {
        if (!initialized) {
            return;
        }
        if (endAnchorFrame != null) {
            try {
                endAnchorFrame.invoke(null, new Object[0]);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                logWarning(exception);
            }
        }
        if (endContext != null) {
            try {
                endContext.invoke(null, new Object[0]);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                logWarning(exception);
            }
        }
    }

    private static synchronized boolean ensureInitialized() {
        if (initialized) {
            return beginContext != null;
        }
        initialized = true;
        try {
            Class<?> contextClass = Class.forName(RENDER_CONTEXT_CLASS);
            Class<?> rendererClass = Class.forName(RENDERER_CLASS);
            Class<?> anchorClass = Class.forName(ANCHOR_SELECTION_CLASS);
            beginContext = contextClass.getMethod("begin", ItemStack.class, ItemDisplayContext.class, MultiBufferSource.class, Integer.TYPE, Integer.TYPE, Float.TYPE, Boolean.TYPE);
            endContext = contextClass.getMethod("end", new Class[0]);
            renderCaptured = rendererClass.getMethod("renderCapturedOnCurrentFrame", PoseStack.class);
            beginAnchorFrame = anchorClass.getMethod("beginRenderedFrame", Boolean.TYPE);
            endAnchorFrame = anchorClass.getMethod("endRenderedFrame", new Class[0]);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError exception) {
            logWarning(exception);
            beginContext = null;
            endContext = null;
            renderCaptured = null;
            beginAnchorFrame = null;
            endAnchorFrame = null;
            return false;
        }
    }

    private static void logWarning(Throwable throwable) {
        if (WARNING_LOGGED.compareAndSet(false, true)) {
            TaczFixesMod.LOGGER.warn("Failed to hook optional TaCZ Charms offhand rendering", throwable);
        }
    }
}
