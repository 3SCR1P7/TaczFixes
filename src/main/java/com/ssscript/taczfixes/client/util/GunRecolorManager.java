package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.ssscript.taczfixes.common.util.GunColorStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 枪械贴图调色: 把贴图像素按 Lab 颜色聚类分组, 每组可指定目标色,
 * 重着色时保留像素相对组均值的饱和度/明度偏移(渐变不丢失), 结果上传为 DynamicTexture。
 * 算法参考独立 HTML 工具(相同流程: Lab + k-means + 合并 + 256x256 s/l 查表)。
 */
public final class GunRecolorManager {
    public static final int MIN_CLUSTERS = 2;
    public static final int MAX_CLUSTERS = 12;
    private static final int DEFAULT_GUN_CLUSTERS = 6;
    private static final int DEFAULT_ATTACHMENT_CLUSTERS = 3;
    private static final int SAMPLE_MAX = 12000;
    private static final float MERGE_DISTANCE = 7.0f;
    private static final int CACHE_LIMIT = 16;
    private static final long KMEANS_SEED = 0x5EEDL;

    private static int gunClusterCount = DEFAULT_GUN_CLUSTERS;
    private static int attachmentClusterCount = DEFAULT_ATTACHMENT_CLUSTERS;
    private static int clusterCount = DEFAULT_GUN_CLUSTERS;

    /** 查询原始贴图时置位, 阻止 getModelTexture 的调色拦截递归/串用。 */
    private static final ThreadLocal<Boolean> SOURCE_LOOKUP = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final ThreadLocal<Deque<ItemStack>> RENDER_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<ResourceLocation, TextureData> TEXTURES = new HashMap<>();
    private static final LinkedHashMap<String, Recached> RECACHED = new LinkedHashMap<>();
    /** 重着色贴图 -> 原始贴图, 供 PBR 查找原 _s/_n 贴图。 */
    private static final Map<ResourceLocation, ResourceLocation> SOURCES = new HashMap<>();

    public record Cluster(int rgb, float r, float g, float b, float h, float s, float l, int count) {
    }

    private record Recached(ResourceLocation rl, DynamicTexture texture) {
    }

    private static final class TextureData {
        final int width;
        final int height;
        final int[] argb;
        final byte[] assign;
        final byte[] pixS;
        final byte[] pixL;
        final List<Cluster> clusters;

        TextureData(int width, int height, int[] argb, byte[] assign, byte[] pixS, byte[] pixL,
                    List<Cluster> clusters) {
            this.width = width;
            this.height = height;
            this.argb = argb;
            this.assign = assign;
            this.pixS = pixS;
            this.pixL = pixL;
            this.clusters = clusters;
        }
    }

    private GunRecolorManager() {
    }

    public static int clusterCount() {
        return clusterCount;
    }

    /** 打开调色面板前按目标类型切换到该类型记住的分组数(枪械默认 6, 配件默认 3)。 */
    public static void applyClusterCountFor(ItemStack target) {
        int wanted = isAttachment(target) ? attachmentClusterCount : gunClusterCount;
        if (wanted == clusterCount) {
            return;
        }
        clusterCount = wanted;
        TEXTURES.clear();
        releaseAll();
    }

    /** 修改分组数: 按目标类型记住, 清空聚类缓存并重置该目标的调色板。 */
    public static void setClusterCount(ItemStack target, int value) {
        int clamped = Math.max(MIN_CLUSTERS, Math.min(MAX_CLUSTERS, value));
        if (isAttachment(target)) {
            attachmentClusterCount = clamped;
        } else {
            gunClusterCount = clamped;
        }
        if (clamped == clusterCount) {
            return;
        }
        clusterCount = clamped;
        TEXTURES.clear();
        releaseAll();
        if (target != null && !target.isEmpty()) {
            GunColorStorage.set(target, new int[0]);
        }
    }

    private static boolean isAttachment(ItemStack item) {
        return item != null && !item.isEmpty() && !(item.getItem() instanceof IGun)
                && IAttachment.getIAttachmentOrNull(item) != null;
    }

    // ---------------- 渲染上下文 ----------------

    public static void pushRenderStack(ItemStack stack) {
        RENDER_STACK.get().push(stack);
    }

    public static void popRenderStack() {
        Deque<ItemStack> stack = RENDER_STACK.get();
        if (!stack.isEmpty()) stack.pop();
    }

    private static ItemStack currentStack() {
        Deque<ItemStack> stack = RENDER_STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * 由 GunDisplayInstance.getModelTexture() 调用。当前渲染物品有调色数据时返回重着色贴图, 否则 null。
     */
    public static ResourceLocation recolored(ResourceLocation original) {
        if (original == null || Boolean.TRUE.equals(SOURCE_LOOKUP.get())) return null;
        ItemStack stack = currentStack();
        if (stack == null || stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof IGun) && !(stack.getItem() instanceof IAttachment)) return null;
        // 只允许用物品自己的贴图做调色, 防止残留/错误的渲染上下文把别的调色板套到当前贴图上
        ResourceLocation own = originalTexture(stack);
        if (own == null || !own.equals(original)) return null;
        TextureData data = textureData(original);
        if (data == null || data.clusters.isEmpty()) return null;
        int[] targets = palette(stack, data.clusters.size());
        if (!hasAnyTarget(targets)) return null;

        String key = original + "#" + Arrays.hashCode(targets);
        Recached cached = RECACHED.get(key);
        if (cached != null) return cached.rl();
        NativeImage image = generate(data, targets);
        if (image == null) return null;
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        ResourceLocation rl = new ResourceLocation("taczfixes",
                "recolor/" + sanitize(original) + "_" + Integer.toHexString(Arrays.hashCode(targets)));
        Minecraft.getInstance().getTextureManager().register(rl, texture);
        RECACHED.put(key, new Recached(rl, texture));
        SOURCES.put(rl, original);
        trimCache();
        return rl;
    }

    /** 重着色贴图对应的原始贴图(非重着色贴图原样返回), PBR 用它定位 _s/_n。 */
    public static ResourceLocation sourceOf(ResourceLocation texture) {
        if (texture == null) return null;
        ResourceLocation source = SOURCES.get(texture);
        return source == null ? texture : source;
    }

    // ---------------- 调色盘 API ----------------

    public static List<Cluster> clustersOf(ItemStack gun) {
        ResourceLocation texture = originalTexture(gun);
        if (texture == null) return List.of();
        TextureData data = textureData(texture);
        return data == null ? List.of() : data.clusters;
    }

    /** 读取枪械已保存的调色板(长度自动补齐到当前分组数, -1 表示原色)。 */
    public static int[] targetsOf(ItemStack gun) {
        List<Cluster> clusters = clustersOf(gun);
        if (clusters.isEmpty()) return new int[0];
        return palette(gun, clusters.size());
    }

    /** 原始(未补长度)保存值, 用于网络同步。 */
    public static int[] rawTargets(ItemStack gun) {
        return GunColorStorage.get(gun);
    }

    public static void setTarget(ItemStack gun, int index, int rgb) {
        List<Cluster> clusters = clustersOf(gun);
        if (index < 0 || index >= clusters.size()) return;
        int[] targets = palette(gun, clusters.size());
        targets[index] = rgb & 0xFFFFFF;
        GunColorStorage.set(gun, targets);
    }

    public static void resetTarget(ItemStack gun, int index) {
        List<Cluster> clusters = clustersOf(gun);
        if (index < 0 || index >= clusters.size()) return;
        int[] targets = palette(gun, clusters.size());
        targets[index] = -1;
        GunColorStorage.set(gun, targets);
    }

    public static void resetAll(ItemStack gun) {
        GunColorStorage.set(gun, new int[0]);
    }

    public static void clearTextures() {
        TEXTURES.clear();
        releaseAll();
    }

    private static void releaseAll() {
        for (Recached cached : RECACHED.values()) {
            Minecraft.getInstance().getTextureManager().release(cached.rl());
        }
        RECACHED.clear();
        SOURCES.clear();
    }

    public static ResourceLocation originalTexture(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        SOURCE_LOOKUP.set(Boolean.TRUE);
        try {
            if (item.getItem() instanceof IGun) {
                return TimelessAPI.getGunDisplay(item).map(GunDisplayInstance::getModelTexture).orElse(null);
            }
            IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
            if (attachment == null) return null;
            ResourceLocation id = attachment.getAttachmentId(item);
            if (id == null) return null;
            return TimelessAPI.getClientAttachmentIndex(id)
                    .map(ClientAttachmentIndex::getModelTexture).orElse(null);
        } finally {
            SOURCE_LOOKUP.set(Boolean.FALSE);
        }
    }

    // ---------------- 数据装载 ----------------

    private static TextureData textureData(ResourceLocation rl) {
        if (TEXTURES.containsKey(rl)) return TEXTURES.get(rl);
        TextureData data = buildTextureData(rl);
        TEXTURES.put(rl, data);
        return data;
    }

    private static TextureData buildTextureData(ResourceLocation rl) {
        NativeImage image = readTexture(rl);
        if (image == null) return null;
        int width = image.getWidth();
        int height = image.getHeight();
        int n = width * height;
        int[] argb = new int[n];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int abgr = image.getPixelRGBA(x, y);
                int a = (abgr >>> 24) & 255;
                int b = (abgr >>> 16) & 255;
                int g = (abgr >>> 8) & 255;
                int r = abgr & 255;
                argb[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        image.close();

        // 1. sRGB -> Lab
        float[] lin = new float[256];
        for (int i = 0; i < 256; i++) lin[i] = srgb2lin(i / 255.0f);
        float[] lab = new float[n * 3];
        for (int i = 0; i < n; i++) {
            int p = argb[i];
            if (((p >>> 24) & 255) < 10) continue;
            float r = lin[(p >>> 16) & 255];
            float g = lin[(p >>> 8) & 255];
            float b = lin[p & 255];
            float x = (r * 0.4124564f + g * 0.3575761f + b * 0.1804375f) / 0.95047f;
            float y = (r * 0.2126729f + g * 0.7151522f + b * 0.0721750f);
            float z = (r * 0.0193339f + g * 0.1191920f + b * 0.9503041f) / 1.08883f;
            x = x > 0.008856f ? (float) Math.cbrt(x) : 7.787f * x + 16.0f / 116.0f;
            y = y > 0.008856f ? (float) Math.cbrt(y) : 7.787f * y + 16.0f / 116.0f;
            z = z > 0.008856f ? (float) Math.cbrt(z) : 7.787f * z + 16.0f / 116.0f;
            lab[i * 3] = 116.0f * y - 16.0f;
            lab[i * 3 + 1] = 500.0f * (x - y);
            lab[i * 3 + 2] = 200.0f * (y - z);
        }

        // 2. 采样
        int step = Math.max(1, n / SAMPLE_MAX);
        int sampleCount = 0;
        for (int i = 0; i < n; i += step) {
            if (((argb[i] >>> 24) & 255) >= 10) sampleCount++;
        }
        float[] samples = new float[sampleCount * 3];
        int si = 0;
        for (int i = 0; i < n; i += step) {
            if (((argb[i] >>> 24) & 255) < 10) continue;
            samples[si++] = lab[i * 3];
            samples[si++] = lab[i * 3 + 1];
            samples[si++] = lab[i * 3 + 2];
        }

        // 3. k-means + 合并
        int k = Math.max(1, Math.min(clusterCount(), si / 3));
        List<float[]> centers = kmeans(samples, si / 3, k);
        centers = mergeCenters(centers, MERGE_DISTANCE);
        int clusterN = Math.max(1, centers.size());

        // 4. 归类 + 统计
        int[] sumR = new int[clusterN];
        int[] sumG = new int[clusterN];
        int[] sumB = new int[clusterN];
        double[] sumS = new double[clusterN];
        double[] sumL = new double[clusterN];
        int[] count = new int[clusterN];
        byte[] assign = new byte[n];
        byte[] pixS = new byte[n];
        byte[] pixL = new byte[n];
        Arrays.fill(assign, (byte) -1);
        for (int i = 0; i < n; i++) {
            int p = argb[i];
            if (((p >>> 24) & 255) < 10) continue;
            float l = lab[i * 3];
            float a = lab[i * 3 + 1];
            float b = lab[i * 3 + 2];
            int best = 0;
            float bestDist = Float.MAX_VALUE;
            for (int c = 0; c < clusterN; c++) {
                float[] center = centers.get(c);
                float dl = l - center[0];
                float da = a - center[1];
                float db = b - center[2];
                float dist = dl * dl + da * da + db * db;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = c;
                }
            }
            assign[i] = (byte) best;
            int r = (p >>> 16) & 255;
            int g = (p >>> 8) & 255;
            int b2 = p & 255;
            int mx = Math.max(r, Math.max(g, b2));
            int mn = Math.min(r, Math.min(g, b2));
            float lf = (mx + mn) / 510.0f;
            float sf = 0.0f;
            if (mx != mn) {
                float d = (mx - mn) / 255.0f;
                sf = lf > 0.5f ? d / (2.0f - mx / 255.0f - mn / 255.0f) : d / (mx / 255.0f + mn / 255.0f);
            }
            pixS[i] = (byte) (int) (sf * 255.0f);
            pixL[i] = (byte) (int) (lf * 255.0f);
            sumR[best] += r;
            sumG[best] += g;
            sumB[best] += b2;
            sumS[best] += sf;
            sumL[best] += lf;
            count[best]++;
        }

        // 5. 分组信息
        List<Cluster> clusters = new ArrayList<>();
        for (int c = 0; c < clusterN; c++) {
            if (count[c] == 0) continue;
            float r = sumR[c] / (float) count[c];
            float g = sumG[c] / (float) count[c];
            float b = sumB[c] / (float) count[c];
            float h = rgbToHsl(r / 255.0f, g / 255.0f, b / 255.0f)[0];
            clusters.add(new Cluster(((int) r << 16) | ((int) g << 8) | (int) b,
                    r, g, b, h, (float) (sumS[c] / count[c]), (float) (sumL[c] / count[c]), count[c]));
        }
        // 空簇被跳过, 重新映射 assign
        if (clusters.size() != clusterN) {
            int[] remap = new int[clusterN];
            Arrays.fill(remap, -1);
            int next = 0;
            for (int c = 0; c < clusterN; c++) {
                if (count[c] > 0) remap[c] = next++;
            }
            for (int i = 0; i < n; i++) {
                int c = assign[i];
                if (c >= 0) assign[i] = (byte) remap[c];
            }
        }
        return new TextureData(width, height, argb, assign, pixS, pixL, clusters);
    }

    private static NativeImage generate(TextureData data, int[] targets) {
        int n = data.width * data.height;
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, data.width, data.height, false);
        int clusterN = data.clusters.size();
        int[][] tables = new int[clusterN][];
        for (int c = 0; c < clusterN; c++) {
            if (c >= targets.length || targets[c] < 0) continue;
            Cluster cluster = data.clusters.get(c);
            float[] hsl = rgbToHsl(((targets[c] >> 16) & 255) / 255.0f,
                    ((targets[c] >> 8) & 255) / 255.0f, (targets[c] & 255) / 255.0f);
            tables[c] = buildTable(hsl[0], hsl[1] - cluster.s(), hsl[2] - cluster.l());
        }
        for (int i = 0; i < n; i++) {
            int p = data.argb[i];
            int a = (p >>> 24) & 255;
            int c = data.assign[i];
            int rgb = p & 0xFFFFFF;
            if (a >= 10 && c >= 0 && c < clusterN && tables[c] != null) {
                int ti = ((data.pixS[i] & 255) << 8) | (data.pixL[i] & 255);
                rgb = tables[c][ti];
            }
            int x = i % data.width;
            int y = i / data.width;
            out.setPixelRGBA(x, y, (a << 24) | (((rgb) & 255) << 16) | (((rgb >>> 8) & 255) << 8) | ((rgb >>> 16) & 255));
        }
        return out;
    }

    // ---------------- 算法工具 ----------------

    private static List<float[]> kmeans(float[] samples, int n, int k) {
        k = Math.min(k, Math.max(1, n));
        List<float[]> centers = new ArrayList<>();
        if (n <= 0) return centers;
        Random random = new Random(KMEANS_SEED);
        int first = random.nextInt(n);
        centers.add(new float[]{samples[first * 3], samples[first * 3 + 1], samples[first * 3 + 2]});
        float[] d2 = new float[n];
        Arrays.fill(d2, Float.MAX_VALUE);
        for (int c = 1; c < k; c++) {
            float[] last = centers.get(c - 1);
            float total = 0.0f;
            for (int i = 0; i < n; i++) {
                float dl = samples[i * 3] - last[0];
                float da = samples[i * 3 + 1] - last[1];
                float db = samples[i * 3 + 2] - last[2];
                float dist = dl * dl + da * da + db * db;
                if (dist < d2[i]) d2[i] = dist;
                total += d2[i];
            }
            int chosen = random.nextInt(n);
            if (total > 1.0E-12f) {
                float r = random.nextFloat() * total;
                float acc = 0.0f;
                for (int i = 0; i < n; i++) {
                    acc += d2[i];
                    if (acc >= r) {
                        chosen = i;
                        break;
                    }
                }
            }
            centers.add(new float[]{samples[chosen * 3], samples[chosen * 3 + 1], samples[chosen * 3 + 2]});
        }

        float[] sums = new float[k * 3];
        int[] counts = new int[k];
        int[] asg = new int[n];
        Arrays.fill(asg, -1);
        for (int iter = 0; iter < 20; iter++) {
            Arrays.fill(sums, 0.0f);
            Arrays.fill(counts, 0);
            for (int i = 0; i < n; i++) {
                float l = samples[i * 3];
                float a = samples[i * 3 + 1];
                float b = samples[i * 3 + 2];
                int best = 0;
                float bestDist = Float.MAX_VALUE;
                for (int c = 0; c < k; c++) {
                    float[] center = centers.get(c);
                    float dl = l - center[0];
                    float da = a - center[1];
                    float db = b - center[2];
                    float dist = dl * dl + da * da + db * db;
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = c;
                    }
                }
                asg[i] = best;
                sums[best * 3] += l;
                sums[best * 3 + 1] += a;
                sums[best * 3 + 2] += b;
                counts[best]++;
            }
            for (int c = 0; c < k; c++) {
                if (counts[c] > 0) {
                    float[] center = centers.get(c);
                    center[0] = sums[c * 3] / counts[c];
                    center[1] = sums[c * 3 + 1] / counts[c];
                    center[2] = sums[c * 3 + 2] / counts[c];
                }
            }
        }
        return centers;
    }

    private static List<float[]> mergeCenters(List<float[]> centers, float threshold) {
        List<float[]> out = new ArrayList<>();
        for (float[] center : centers) out.add(center.clone());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < out.size() && !changed; i++) {
                for (int j = i + 1; j < out.size(); j++) {
                    float dl = out.get(i)[0] - out.get(j)[0];
                    float da = out.get(i)[1] - out.get(j)[1];
                    float db = out.get(i)[2] - out.get(j)[2];
                    if (Math.sqrt(dl * dl + da * da + db * db) < threshold) {
                        out.get(i)[0] = (out.get(i)[0] + out.get(j)[0]) / 2.0f;
                        out.get(i)[1] = (out.get(i)[1] + out.get(j)[1]) / 2.0f;
                        out.get(i)[2] = (out.get(i)[2] + out.get(j)[2]) / 2.0f;
                        out.remove(j);
                        changed = true;
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static int[] buildTable(float h, float dS, float dL) {
        int[] table = new int[65536];
        for (int si = 0; si < 256; si++) {
            float s = clamp01(si / 255.0f + dS);
            int base = si << 8;
            for (int li = 0; li < 256; li++) {
                float l = clamp01(li / 255.0f + dL);
                table[base | li] = hslToRgb(h, s, l);
            }
        }
        return table;
    }

    public static int hslToRgb(float h, float s, float l) {
        h = h - (float) Math.floor(h);
        if (s <= 1.0E-6f) {
            int v = (int) Math.round(l * 255.0f);
            return (v << 16) | (v << 8) | v;
        }
        float q = l < 0.5f ? l * (1.0f + s) : l + s - l * s;
        float p = 2.0f * l - q;
        float r = hue2rgb(p, q, h + 1.0f / 3.0f);
        float g = hue2rgb(p, q, h);
        float b = hue2rgb(p, q, h - 1.0f / 3.0f);
        return (Math.round(r * 255.0f) << 16) | (Math.round(g * 255.0f) << 8) | Math.round(b * 255.0f);
    }

    public static float[] rgbToHsl(float r, float g, float b) {
        float mx = Math.max(r, Math.max(g, b));
        float mn = Math.min(r, Math.min(g, b));
        float l = (mx + mn) / 2.0f;
        float h = 0.0f;
        float s = 0.0f;
        if (mx != mn) {
            float d = mx - mn;
            s = l > 0.5f ? d / (2.0f - mx - mn) : d / (mx + mn);
            if (mx == r) {
                h = (g - b) / d + (g < b ? 6.0f : 0.0f);
            } else if (mx == g) {
                h = (b - r) / d + 2.0f;
            } else {
                h = (r - g) / d + 4.0f;
            }
            h /= 6.0f;
        }
        return new float[]{h, s, l};
    }

    private static float hue2rgb(float p, float q, float t) {
        if (t < 0.0f) t += 1.0f;
        if (t > 1.0f) t -= 1.0f;
        if (t < 1.0f / 6.0f) return p + (q - p) * 6.0f * t;
        if (t < 1.0f / 2.0f) return q;
        if (t < 2.0f / 3.0f) return p + (q - p) * (2.0f / 3.0f - t) * 6.0f;
        return p;
    }

    private static float srgb2lin(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    private static boolean hasAnyTarget(int[] targets) {
        for (int target : targets) {
            if (target >= 0) return true;
        }
        return false;
    }

    private static int[] palette(ItemStack gun, int size) {
        int[] stored = GunColorStorage.get(gun);
        int[] targets = new int[size];
        Arrays.fill(targets, -1);
        System.arraycopy(stored, 0, targets, 0, Math.min(stored.length, size));
        return targets;
    }

    private static void trimCache() {
        while (RECACHED.size() > CACHE_LIMIT) {
            String oldest = RECACHED.keySet().iterator().next();
            Recached removed = RECACHED.remove(oldest);
            if (removed != null) {
                Minecraft.getInstance().getTextureManager().release(removed.rl());
                SOURCES.remove(removed.rl());
            }
        }
    }

    private static String sanitize(ResourceLocation rl) {
        return (rl.getNamespace() + "_" + rl.getPath()).replaceAll("[^a-z0-9_./-]", "_");
    }

    private static NativeImage readTexture(ResourceLocation rl) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        Path taczDir = gameDir.resolve("tacz");
        String path = rl.getPath();
        String relative;
        if (path.startsWith("textures/")) {
            relative = "assets/" + rl.getNamespace() + "/" + path + (path.endsWith(".png") ? "" : ".png");
        } else if (path.endsWith(".png")) {
            relative = "assets/" + rl.getNamespace() + "/" + path;
        } else {
            relative = "assets/" + rl.getNamespace() + "/textures/" + path + ".png";
        }
        if (Files.isDirectory(taczDir)) {
            try (Stream<Path> children = Files.list(taczDir)) {
                List<Path> list = children.sorted().toList();
                for (Path child : list) {
                    NativeImage image = readFromPath(child, relative);
                    if (image != null) return image;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open()) {
                    return NativeImage.read(NativeImage.Format.RGBA, in);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static NativeImage readFromPath(Path child, String relative) {
        try {
            if (Files.isDirectory(child)) {
                Path file = child.resolve(relative);
                if (!Files.isRegularFile(file)) return null;
                try (InputStream in = Files.newInputStream(file)) {
                    return NativeImage.read(NativeImage.Format.RGBA, in);
                }
            } else if (Files.isRegularFile(child)) {
                try (ZipFile zip = new ZipFile(child.toFile())) {
                    ZipEntry entry = zip.getEntry(relative);
                    if (entry == null) return null;
                    try (InputStream in = zip.getInputStream(entry)) {
                        return NativeImage.read(NativeImage.Format.RGBA, in);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
