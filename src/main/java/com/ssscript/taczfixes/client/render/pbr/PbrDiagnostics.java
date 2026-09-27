package com.ssscript.taczfixes.client.render.pbr;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.logging.LogUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

/** Bounded startup diagnostics: no per-frame GPU readback after three samples. */
public final class PbrDiagnostics {
    private static final Set<String> groups = new HashSet<>();
    private static int samples;
    private static long nextSample;
    private static boolean sample, postSample;
    private static boolean compositeSample;
    private static byte[] before;
    public static void group(String name) {
        if (groups.size() < 8 && groups.add(name)) LogUtils.getLogger().info("[TaCZ PBR] illuminated group: {}", name);
    }
    public static void vertices(BufferBuilder.RenderedBuffer data) {
        sample = false;
        if (samples >= 3 || System.nanoTime() < nextSample) return;
        var bytes = data.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
        int count = 0;
        // NEW_ENTITY layout: position/color/uv/overlay/light/normal, 36 bytes per vertex.
        for (int i = 28; i + 1 < bytes.limit(); i += 36) if ((bytes.getShort(i) & 1) != 0) count++;
        if (count == 0) return;
        sample = true; samples++; nextSample = System.nanoTime() + 5_000_000_000L;
        LogUtils.getLogger().info("[TaCZ PBR] marked vertices={}, drawFramebuffer={}, captureEnabled={}",
                count, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), PbrBloom.canCapture());
    }
    public static void capture(RenderTarget target) {
        if (!sample) return;
        sample = false;
        postSample = true;
        readPixels(target, "emission");
    }
    public static void blurred(RenderTarget target) {
        if (!postSample) return;
        postSample = false;
        compositeSample = true;
        readPixels(target, "blurred");
    }
    public static void beforeComposite(RenderTarget target) {
        if (!compositeSample) return;
        before = snapshot(target);
    }
    public static void afterComposite(RenderTarget target) {
        if (!compositeSample || before == null) return;
        compositeSample = false;
        byte[] after = snapshot(target);
        int changed = 0, peak = 0;
        for (int i = 0; i < after.length; i += 4) {
            int delta = 0;
            for (int c = 0; c < 3; c++) delta = Math.max(delta, (after[i+c]&255)-(before[i+c]&255));
            if (delta > 0) changed++;
            peak = Math.max(peak, delta);
        }
        LogUtils.getLogger().info("[TaCZ PBR] composite brightened pixels={}, maxDelta={}, blend={}/{}, GLerror={}",
                changed, peak, GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB), GL11.glGetError());
        before = null;
    }
    private static byte[] snapshot(RenderTarget target) {
        int previous = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        var pixels = MemoryUtil.memAlloc(target.width * target.height * 4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GL11.glReadPixels(0,0,target.width,target.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            byte[] result = new byte[pixels.remaining()]; pixels.get(result); return result;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous);
            MemoryUtil.memFree(pixels);
        }
    }
    private static void readPixels(RenderTarget target, String stage) {
        int previous = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        var pixels = MemoryUtil.memAlloc(target.width * target.height * 4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GL11.glReadPixels(0, 0, target.width, target.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            int lit = 0, peak = 0;
            for (int i = 0; i < pixels.limit(); i += 4) {
                int value = Math.max(pixels.get(i) & 255, Math.max(pixels.get(i + 1) & 255, pixels.get(i + 2) & 255));
                if (value != 0) lit++;
                peak = Math.max(peak, value);
            }
            LogUtils.getLogger().info("[TaCZ PBR] {} pixels={}, peak={}, depthFunc={}, stencil={}",
                    stage, lit, peak, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), GL11.glIsEnabled(GL11.GL_STENCIL_TEST));
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous);
            MemoryUtil.memFree(pixels);
        }
    }
    private PbrDiagnostics() {}
}
