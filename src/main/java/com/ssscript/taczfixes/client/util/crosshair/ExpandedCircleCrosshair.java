package com.ssscript.taczfixes.client.util.crosshair;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ssscript.taczfixes.TaczFixesMod;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ExpandedCircleCrosshair {
    private static final int SIZE = 256;
    /** 动态贴图缓存上限: 3 种圆形准星贴图 × 约 37 个半像素偏移档, 再留余量。 */
    private static final int MAX_TEXTURES = 128;
    /** accessOrder=true: 读缓存即算一次访问, 超限时淘汰最久未使用的贴图。 */
    private static final Map<String, ResourceLocation> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private ExpandedCircleCrosshair() {
    }

    public static ResourceLocation get(CrosshairPartLayout layout, float offset) {
        float quantized = Math.round(offset * 2.0f) * 0.5f;
        // 用布局来源贴图代替 identityHashCode: 布局对象被回收后哈希可能被复用, 会取到别的准星的错误贴图。
        String key = (layout.sourceTexture() == null ? "empty" : layout.sourceTexture().toString()) + ":" + quantized;
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        NativeImage image = render(layout, quantized);
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID,
                "dynamic_crosshair/" + NEXT_ID.incrementAndGet());
        Minecraft.getInstance().getTextureManager().register(location, texture);
        CACHE.put(key, location);
        evictOverflow();
        return location;
    }

    /** 资源重载时由客户端资源重载链调用: 释放全部已注册动态贴图并清空缓存。 */
    public static void clear() {
        int count = CACHE.size();
        if (count > 0) {
            if (RenderSystem.isOnRenderThread()) {
                TextureManager manager = Minecraft.getInstance().getTextureManager();
                // 在渲染线程才能安全释放 GL 贴图。
                for (ResourceLocation location : CACHE.values()) {
                    manager.release(location);
                }
            } else {
                // 不在渲染线程时释放贴图不安全: 只清缓存并记录, 宁可泄漏也不能崩溃。
                TaczFixesMod.LOGGER.warn(
                        "taczfixes: skipped releasing {} dynamic crosshair textures outside the render thread", count);
            }
        }
        CACHE.clear();
    }

    /** 超过上限时按访问顺序淘汰最久未用的贴图并立即释放, 避免动态贴图无限增长。 */
    private static void evictOverflow() {
        if (CACHE.size() <= MAX_TEXTURES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Iterator<Map.Entry<String, ResourceLocation>> iterator = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_TEXTURES && iterator.hasNext()) {
            ResourceLocation stale = iterator.next().getValue();
            iterator.remove();
            minecraft.getTextureManager().release(stale);
        }
    }

    private static NativeImage render(CrosshairPartLayout layout, float offset) {
        NativeImage image = new NativeImage(SIZE, SIZE, false);
        image.fillRect(0, 0, SIZE, SIZE, 0);
        int width = layout.width();
        int height = layout.height();
        boolean[] expanded = new boolean[width * height];
        boolean[] centered = new boolean[width * height];
        for (CrosshairPartLayout.Part part : layout.parts()) {
            boolean[] target = part.centered() ? centered : expanded;
            for (CrosshairPartLayout.Strip strip : part.strips()) {
                for (int y = strip.y(); y < strip.y() + strip.height(); y++) {
                    for (int x = strip.x(); x < strip.x() + strip.width(); x++) {
                        if (x >= 0 && y >= 0 && x < width && y < height) {
                            target[y * width + x] = true;
                        }
                    }
                }
            }
        }
        float center = SIZE * 0.5f;
        float sourcePerGui = width / 16.0f;
        float guiPerCanvas = 64.0f / SIZE;
        float sourceToCanvas = SIZE / 512.0f;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float dx = x + 0.5f - center;
                float dy = y + 0.5f - center;
                float distance = Mth.sqrt(dx * dx + dy * dy);
                if (distance <= 0.001f) {
                    continue;
                }
                float sourceDistance = distance * guiPerCanvas - offset;
                if (sourceDistance < 0.0f) {
                    continue;
                }
                float ratio = sourceDistance * sourcePerGui / distance;
                int sourceX = Math.max(0, Math.min(width - 1,
                        Math.round(width * 0.5f + dx * ratio)));
                int sourceY = Math.max(0, Math.min(height - 1,
                        Math.round(height * 0.5f + dy * ratio)));
                if (!expanded[sourceY * width + sourceX]) {
                    continue;
                }
                int alpha = layout.alphaAt(sourceX, sourceY);
                if (alpha == 0) {
                    continue;
                }
                image.setPixelRGBA(x, y, alpha << 24 | 0xFFFFFF);
            }
        }
        // Centered parts (the dot) must not undergo radial dilation.
        for (int sy = 0; sy < height; sy++) {
            for (int sx = 0; sx < width; sx++) {
                if (!centered[sy * width + sx]) {
                    continue;
                }
                int alpha = layout.alphaAt(sx, sy);
                if (alpha == 0) {
                    continue;
                }
                int ox = Math.round(center + (sx - width * 0.5f) * sourceToCanvas);
                int oy = Math.round(center + (sy - height * 0.5f) * sourceToCanvas);
                if (ox >= 0 && oy >= 0 && ox < SIZE && oy < SIZE) {
                    image.setPixelRGBA(ox, oy, alpha << 24 | 0xFFFFFF);
                }
            }
        }
        return image;
    }
}
