package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 准星贴图的连通区域分析: 每个连通的白色部分作为一个整体处理,
 * 普通部分沿"远离中心"的方向整体平移, 包裹中心的环形部分整体向外缩放,
 * 中心部分(如中心点)保持不动。这样不会把连在一起的图形切碎。
 */
@OnlyIn(Dist.CLIENT)
public final class CrosshairPartLayout {
    private static final Map<ResourceLocation, CrosshairPartLayout> CACHE = new HashMap<>();
    private static final CrosshairPartLayout EMPTY = new CrosshairPartLayout(0, 0, List.of());

    /** 贴图中一个 1 至多像素高的连续横条。 */
    public record Strip(int x, int y, int width, int height) {
    }

    /** 一个连通部分: 若干横条 + 远离中心的方向; ringLike 表示整体缩放。 */
    public record Part(List<Strip> strips, float dirX, float dirY, boolean ringLike, boolean centered) {
    }

    private final int width;
    private final int height;
    private final List<Part> parts;

    private CrosshairPartLayout(int width, int height, List<Part> parts) {
        this.width = width;
        this.height = height;
        this.parts = parts;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public List<Part> parts() {
        return this.parts;
    }

    public static void clear() {
        CACHE.clear();
    }

    public static CrosshairPartLayout get(ResourceLocation texture) {
        if (texture == null) {
            return EMPTY;
        }
        CrosshairPartLayout cached = CACHE.get(texture);
        if (cached != null) {
            return cached;
        }
        CrosshairPartLayout loaded = load(texture);
        CACHE.put(texture, loaded);
        return loaded;
    }

    private static CrosshairPartLayout load(ResourceLocation texture) {
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
            if (resource == null) {
                return EMPTY;
            }
            NativeImage image;
            try (InputStream stream = resource.open()) {
                image = NativeImage.read(stream);
            }
            CrosshairPartLayout layout = analyze(image);
            image.close();
            return layout;
        } catch (Throwable ignored) {
            return EMPTY;
        }
    }

    private static CrosshairPartLayout analyze(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= 0 || h <= 0) {
            return EMPTY;
        }
        boolean[] opaque = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                opaque[y * w + x] = (image.getPixelRGBA(x, y) >>> 24) != 0;
            }
        }
        int[] labels = new int[w * h];
        java.util.Arrays.fill(labels, -1);
        List<Part> parts = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int[] dxs = {1, -1, 0, 0};
        int[] dys = {0, 0, 1, -1};
        float centerX = (w - 1) / 2.0f;
        float centerY = (h - 1) / 2.0f;
        for (int start = 0; start < w * h; start++) {
            if (!opaque[start] || labels[start] >= 0) {
                continue;
            }
            labels[start] = parts.size();
            queue.add(start);
            Map<Integer, List<Integer>> rowPixels = new HashMap<>();
            long sumX = 0L;
            long sumY = 0L;
            long count = 0L;
            int minX = w;
            int minY = h;
            int maxX = -1;
            int maxY = -1;
            while (!queue.isEmpty()) {
                int index = queue.poll();
                int px = index % w;
                int py = index / w;
                rowPixels.computeIfAbsent(py, key -> new ArrayList<>()).add(px);
                sumX += px;
                sumY += py;
                count++;
                minX = Math.min(minX, px);
                maxX = Math.max(maxX, px);
                minY = Math.min(minY, py);
                maxY = Math.max(maxY, py);
                for (int direction = 0; direction < 4; direction++) {
                    int nx = px + dxs[direction];
                    int ny = py + dys[direction];
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                        continue;
                    }
                    int neighbor = ny * w + nx;
                    if (opaque[neighbor] && labels[neighbor] < 0) {
                        labels[neighbor] = labels[start];
                        queue.add(neighbor);
                    }
                }
            }
            if (count <= 0L) {
                continue;
            }
            float centroidX = (float) (sumX / (double) count);
            float centroidY = (float) (sumY / (double) count);
            float dirX = centroidX - centerX;
            float dirY = centroidY - centerY;
            float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (length > 1.0e-3f) {
                dirX /= length;
                dirY /= length;
            } else {
                dirX = 0.0f;
                dirY = 0.0f;
            }
            boolean containsCenter = minX <= centerX && centerX <= maxX && minY <= centerY && centerY <= maxY;
            boolean ringLike = containsCenter && (maxX - minX + 1) >= w * 0.5f && (maxY - minY + 1) >= h * 0.5f;
            boolean centered = !ringLike && (containsCenter || length < Math.max(2.5f, w * 0.05f));
            parts.add(new Part(buildStrips(rowPixels), dirX, dirY, ringLike, centered));
        }
        return new CrosshairPartLayout(w, h, parts);
    }

    private static List<Strip> buildStrips(Map<Integer, List<Integer>> rowPixels) {
        List<Strip> strips = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : rowPixels.entrySet()) {
            int y = entry.getKey();
            List<Integer> xs = entry.getValue();
            xs.sort(Integer::compareTo);
            int runStart = xs.get(0);
            int runEnd = runStart;
            for (int index = 1; index < xs.size(); index++) {
                int x = xs.get(index);
                if (x == runEnd + 1) {
                    runEnd = x;
                    continue;
                }
                strips.add(new Strip(runStart, y, runEnd - runStart + 1, 1));
                runStart = x;
                runEnd = x;
            }
            strips.add(new Strip(runStart, y, runEnd - runStart + 1, 1));
        }
        strips.sort((first, second) -> first.y() != second.y()
                ? Integer.compare(first.y(), second.y())
                : Integer.compare(first.x(), second.x()));
        List<Strip> merged = new ArrayList<>();
        for (Strip strip : strips) {
            if (!merged.isEmpty()) {
                Strip last = merged.get(merged.size() - 1);
                if (last.x() == strip.x() && last.width() == strip.width() && last.y() + last.height() == strip.y()) {
                    merged.set(merged.size() - 1, new Strip(last.x(), last.y(), last.width(), last.height() + strip.height()));
                    continue;
                }
            }
            merged.add(strip);
        }
        return merged;
    }
}
