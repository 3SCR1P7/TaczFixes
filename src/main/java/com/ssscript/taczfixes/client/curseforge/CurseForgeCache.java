package com.ssscript.taczfixes.client.curseforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ssscript.taczfixes.common.util.TaczFixesCache;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CurseForge 搜索/文件列表的持久化缓存: 内存映射 + taczfixes_cache/curseforge_search_cache.json。
 *
 * 命中直接返回, 跨游戏重启依然有效; 只缓存成功响应(网络失败不写入)。
 * 写盘在后台线程完成(临时文件 + 原子替换), 文件损坏时静默丢弃。
 */
public final class CurseForgeCache {
    private static final Gson GSON = new GsonBuilder().create();
    /** 缓存有效期: 30~60 分钟之间, 取 45 分钟。 */
    private static final long TTL_MS = 45L * 60L * 1000L;
    /** 最大缓存条目数, 超出后淘汰最旧的(搜索页 + 文件列表共用)。 */
    private static final int MAX_ENTRIES = 32;
    private static final String FILE_NAME = "curseforge_search_cache.json";
    private static final int FORMAT_VERSION = 1;

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final AtomicBoolean LOADED = new AtomicBoolean();
    private static final AtomicBoolean SAVE_SCHEDULED = new AtomicBoolean();
    private static final Object IO_LOCK = new Object();

    private record Entry(long timestamp, JsonObject payload) {
    }

    private CurseForgeCache() {
    }

    /** 提前在后台把缓存文件读进内存, 避免第一次搜索时在主线程读盘。 */
    public static void warmUp() {
        if (LOADED.get()) return;
        try {
            CurseForgeApi.EXECUTOR.execute(CurseForgeCache::ensureLoaded);
        } catch (Throwable ignored) {
        }
    }

    /** 取缓存中的原始响应(未命中/已过期返回 null)。 */
    public static JsonObject get(String key) {
        if (key == null) return null;
        ensureLoaded();
        Entry entry = ENTRIES.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.timestamp() > TTL_MS) {
            ENTRIES.remove(key);
            return null;
        }
        return entry.payload();
    }

    /** 缓存一次成功响应, 并在后台异步落盘。 */
    public static void put(String key, JsonObject payload) {
        if (key == null || payload == null) return;
        ensureLoaded();
        long now = System.currentTimeMillis();
        ENTRIES.put(key, new Entry(now, payload));
        trim(now);
        scheduleSave();
    }

    private static void trim(long now) {
        if (ENTRIES.size() <= MAX_ENTRIES) {
            return;
        }
        List<Map.Entry<String, Entry>> sorted = new ArrayList<>(ENTRIES.entrySet());
        sorted.sort(Comparator.comparingLong(entry -> entry.getValue().timestamp()));
        int remove = ENTRIES.size() - MAX_ENTRIES;
        for (Map.Entry<String, Entry> entry : sorted) {
            if (remove <= 0) break;
            ENTRIES.remove(entry.getKey());
            remove--;
        }
    }

    private static void ensureLoaded() {
        if (!LOADED.compareAndSet(false, true)) return;
        Path file = cacheFile();
        if (file == null || !Files.isRegularFile(file)) return;
        synchronized (IO_LOCK) {
            try {
                String raw = Files.readString(file, StandardCharsets.UTF_8);
                JsonElement element = JsonParser.parseString(raw);
                if (!element.isJsonObject()) return;
                JsonObject root = element.getAsJsonObject();
                if (!root.has("entries") || !root.get("entries").isJsonObject()) return;
                long now = System.currentTimeMillis();
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("entries").entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject value = entry.getValue().getAsJsonObject();
                    if (!value.has("timestamp") || !value.has("payload")) continue;
                    long timestamp;
                    try {
                        timestamp = value.get("timestamp").getAsLong();
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (now - timestamp > TTL_MS) continue;
                    JsonElement payload = value.get("payload");
                    if (payload == null || !payload.isJsonObject()) continue;
                    ENTRIES.put(entry.getKey(), new Entry(timestamp, payload.getAsJsonObject()));
                }
                trim(now);
            } catch (Throwable ignored) {
                // 文件损坏/被占用: 直接丢弃, 下次写入会覆盖
                ENTRIES.clear();
            }
        }
    }

    private static void scheduleSave() {
        if (!SAVE_SCHEDULED.compareAndSet(false, true)) return;
        try {
            CurseForgeApi.EXECUTOR.execute(() -> {
                SAVE_SCHEDULED.set(false);
                save();
            });
        } catch (Throwable ignored) {
            SAVE_SCHEDULED.set(false);
        }
    }

    private static void save() {
        Path file = cacheFile();
        if (file == null) return;
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        JsonObject entries = new JsonObject();
        for (Map.Entry<String, Entry> entry : ENTRIES.entrySet()) {
            JsonObject value = new JsonObject();
            value.addProperty("timestamp", entry.getValue().timestamp());
            value.add("payload", entry.getValue().payload());
            entries.add(entry.getKey(), value);
        }
        root.add("entries", entries);
        String raw = GSON.toJson(root);
        synchronized (IO_LOCK) {
            try {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Path temp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(temp, raw, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicMoveFailed) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static Path cacheFile() {
        try {
            return TaczFixesCache.file(FILE_NAME);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
