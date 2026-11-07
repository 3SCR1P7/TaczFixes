package com.ssscript.taczfixes.client.curseforge;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CurseForge 项目封面(logo)的异步加载与缓存。
 *
 * <p>搜索结果里只按需(渲染到可见行时)下载 64x64 缩略图, 默认优先走国内镜像;
 * 下载完成后在客户端线程注册为 DynamicTexture。缓存有上限(200 张), 超出时释放最久未用的贴图。</p>
 */
public final class CurseForgeImages {
    /** 已加载的封面。 */
    public record Icon(ResourceLocation location, int width, int height) {
    }

    private static final int MAX_CACHED = 200;
    private static final int MAX_BYTES = 1024 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "taczfixes-cf-icons");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, Icon> LOADED = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Icon> eldest) {
                    if (size() <= MAX_CACHED) return false;
                    release(eldest.getValue());
                    return true;
                }
            });

    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private CurseForgeImages() {
    }

    /** 已加载的封面, 没有则返回 null(不会触发下载)。 */
    public static Icon get(String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) return null;
        return LOADED.get(thumbnailUrl);
    }

    /**
     * 渲染时调用: 已加载直接返回图标, 否则在后台下载并返回 null(下一帧显示占位)。
     */
    public static Icon request(String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) return null;
        Icon icon = LOADED.get(thumbnailUrl);
        if (icon != null) return icon;
        if (FAILED.contains(thumbnailUrl) || !PENDING.add(thumbnailUrl)) return null;
        EXECUTOR.execute(() -> {
            byte[] data = null;
            try {
                data = download(thumbnailUrl);
            } catch (Throwable ignored) {
            }
            if (data == null) {
                FAILED.add(thumbnailUrl);
                PENDING.remove(thumbnailUrl);
                return;
            }
            final byte[] imageData = data;
            Minecraft.getInstance().execute(() -> {
                try {
                    register(thumbnailUrl, imageData);
                } catch (Throwable ignored) {
                    FAILED.add(thumbnailUrl);
                } finally {
                    PENDING.remove(thumbnailUrl);
                }
            });
        });
        return null;
    }

    /** 清空并释放所有封面贴图。 */
    public static void clear() {
        List<Icon> icons;
        synchronized (LOADED) {
            icons = new ArrayList<>(LOADED.values());
            LOADED.clear();
        }
        for (Icon icon : icons) {
            release(icon);
        }
        FAILED.clear();
    }

    /** 允许之前加载失败的封面重试(例如重开界面时网络已恢复)。 */
    public static void clearFailed() {
        FAILED.clear();
    }

    private static void register(String url, byte[] data) throws IOException {
        NativeImage image = NativeImage.read(data);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("taczfixes", "curseforge/" + hash(url));
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(location, texture);
        LOADED.put(url, new Icon(location, image.getWidth(), image.getHeight()));
    }

    private static void release(Icon icon) {
        if (icon == null) return;
        try {
            Minecraft.getInstance().getTextureManager().release(icon.location());
        } catch (Throwable ignored) {
        }
    }

    private static byte[] download(String thumbnailUrl) throws IOException {
        List<String> candidates = candidateUrls(thumbnailUrl);
        IOException last = null;
        for (String url : candidates) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .header("User-Agent", "taczfixes/2.1.1 (Minecraft-Forge-1.20.1; gunpack-browser)")
                        .header("Accept", "image/*,*/*")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() / 100 != 2) {
                    try (InputStream ignored = response.body()) {
                        // 关闭响应体
                    }
                    continue;
                }
                try (InputStream in = response.body()) {
                    byte[] data = in.readNBytes(MAX_BYTES + 1);
                    if (data.length == 0 || data.length > MAX_BYTES) continue;
                    // 只接受 PNG(CurseForge 封面都是 PNG), 防止把 HTML 错误页当成图片
                    if (data.length < 8 || (data[0] & 0xFF) != 0x89 || data[1] != 'P'
                            || data[2] != 'N' || data[3] != 'G') {
                        continue;
                    }
                    return data;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("cancelled");
            } catch (IOException | RuntimeException error) {
                last = error instanceof IOException io ? io : new IOException(error);
            }
        }
        throw last == null ? new IOException("no image candidate") : last;
    }

    /**
     * 候选地址: 国内镜像优先, 其次官方 CDN; 64x64 缩略图优先, 减少带宽与下载时间。
     */
    private static List<String> candidateUrls(String thumbnailUrl) {
        List<String> urls = new ArrayList<>();
        String small = thumbnailUrl.replace("/256/256/", "/64/64/");
        String mirror = mirrorBase();
        String smallPath = pathOf(small);
        String fullPath = pathOf(thumbnailUrl);
        if (mirror != null) {
            if (smallPath != null) urls.add(mirror + smallPath);
            if (fullPath != null) urls.add(mirror + fullPath);
        }
        urls.add(small);
        if (!small.equals(thumbnailUrl)) urls.add(thumbnailUrl);
        return new ArrayList<>(new LinkedHashSet<>(urls));
    }

    /** 当前配置的镜像主机(不含路径), 非镜像配置时返回 null。 */
    private static String mirrorBase() {
        try {
            URI api = URI.create(CurseForgeConfig.apiBase());
            String host = api.getHost();
            if (host != null && host.contains("mcimirror")) {
                return api.getScheme() + "://" + host;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String pathOf(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null || path.isEmpty() ? null : path;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                builder.append(Character.forDigit((bytes[i] >> 4) & 0xF, 16));
                builder.append(Character.forDigit(bytes[i] & 0xF, 16));
            }
            return builder.toString();
        } catch (Throwable ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
