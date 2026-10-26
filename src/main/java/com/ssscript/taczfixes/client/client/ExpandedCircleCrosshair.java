package com.ssscript.taczfixes.client.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.ssscript.taczfixes.TaczFixesMod;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ExpandedCircleCrosshair {
    private static final int SIZE = 256;
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private ExpandedCircleCrosshair() {
    }

    public static ResourceLocation get(CrosshairPartLayout layout, float offset) {
        float quantized = Math.round(offset * 2.0f) * 0.5f;
        String key = System.identityHashCode(layout) + ":" + quantized;
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
        return location;
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
