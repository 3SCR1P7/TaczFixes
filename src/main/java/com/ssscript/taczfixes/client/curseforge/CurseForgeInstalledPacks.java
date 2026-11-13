package com.ssscript.taczfixes.client.curseforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ssscript.taczfixes.common.data.GunPackInfoScanner;
import com.ssscript.taczfixes.common.util.TaczFixesCache;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 记录“从 CurseForge 下载安装过的枪包”, 持久化于 taczfixes_cache/curseforge_installed.json。
 * 枪包管理界面据此在后台检查更新(对比最新文件 id)。写盘在后台线程完成。
 */
public final class CurseForgeInstalledPacks {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "curseforge_installed.json";
    private static final int FORMAT_VERSION = 1;

    /** 一条安装记录。fileDate 是安装时 CurseForge 文件的发布时间(0 表示旧记录未记录)。 */
    public record Installed(long modId, long fileId, String fileName, String displayName, String namespace,
                            long fileDate, long installedAt) {
    }

    private static final List<Installed> ENTRIES = new ArrayList<>();
    private static final AtomicBoolean LOADED = new AtomicBoolean();
    private static final AtomicBoolean SAVE_SCHEDULED = new AtomicBoolean();
    private static final Object LOCK = new Object();

    private CurseForgeInstalledPacks() {
    }

    public static List<Installed> all() {
        ensureLoaded();
        synchronized (LOCK) {
            return List.copyOf(ENTRIES);
        }
    }

    /**
     * 把安装记录对应到枪包管理界面里的实际枪包 ID(namespace@文件名)。
     * 优先 namespace + 文件名同时匹配, 其次唯一 namespace, 最后唯一文件名;
     * 同名 namespace 有多个枪包时不会贸然对应, 避免红点标错。
     */
    public static Map<String, Installed> matchPacks(List<GunPackInfoScanner.PackInfoData> packs) {
        if (packs == null || packs.isEmpty()) return Map.of();
        List<Installed> records = all();
        if (records.isEmpty()) return Map.of();
        Map<String, Installed> result = new HashMap<>();
        Set<Long> matched = new HashSet<>();

        for (GunPackInfoScanner.PackInfoData pack : packs) {
            for (Installed record : records) {
                if (matched.contains(record.modId())) continue;
                if (sameNamespace(record.namespace(), pack.name()) && sameFileName(record.fileName(), pack.path())) {
                    result.put(pack.id(), record);
                    matched.add(record.modId());
                    break;
                }
            }
        }

        for (GunPackInfoScanner.PackInfoData pack : packs) {
            if (result.containsKey(pack.id())) continue;
            Installed unique = null;
            for (Installed record : records) {
                if (matched.contains(record.modId()) || !sameNamespace(record.namespace(), pack.name())) continue;
                if (unique != null) {
                    unique = null;
                    break;
                }
                unique = record;
            }
            if (unique != null) {
                result.put(pack.id(), unique);
                matched.add(unique.modId());
            }
        }

        for (GunPackInfoScanner.PackInfoData pack : packs) {
            if (result.containsKey(pack.id())) continue;
            Installed unique = null;
            for (Installed record : records) {
                if (matched.contains(record.modId()) || !sameFileName(record.fileName(), pack.path())) continue;
                if (unique != null) {
                    unique = null;
                    break;
                }
                unique = record;
            }
            if (unique != null) {
                result.put(pack.id(), unique);
                matched.add(unique.modId());
            }
        }
        return Map.copyOf(result);
    }

    private static boolean sameNamespace(String installedNamespace, String packNamespace) {
        if (installedNamespace == null || installedNamespace.isBlank()
                || packNamespace == null || packNamespace.isBlank()) {
            return false;
        }
        return installedNamespace.equalsIgnoreCase(packNamespace);
    }

    private static boolean sameFileName(String installedFileName, Path packPath) {
        if (installedFileName == null || installedFileName.isBlank() || packPath == null
                || packPath.getFileName() == null) {
            return false;
        }
        return installedFileName.equalsIgnoreCase(packPath.getFileName().toString());
    }

    /** 记录一次安装: 同一 modId 的旧记录会被覆盖。 */
    public static void recordInstall(CurseForgeApi.FileInfo file, Path installedFile, String namespace) {
        if (file == null || installedFile == null) return;
        ensureLoaded();
        String fileName = installedFile.getFileName() == null ? "" : installedFile.getFileName().toString();
        Installed entry = new Installed(file.modId(), file.fileId(), fileName, file.displayName(),
                namespace, file.date(), System.currentTimeMillis());
        synchronized (LOCK) {
            ENTRIES.removeIf(existing -> existing.modId() == entry.modId());
            ENTRIES.add(entry);
        }
        scheduleSave();
    }

    /** 更新成功后就地修改文件信息(保持 modId 与 namespace)。 */
    public static void updateFile(long modId, CurseForgeApi.FileInfo file, Path installedFile, String namespace) {
        if (file == null || installedFile == null) return;
        ensureLoaded();
        String fileName = installedFile.getFileName() == null ? "" : installedFile.getFileName().toString();
        synchronized (LOCK) {
            for (int i = 0; i < ENTRIES.size(); i++) {
                Installed existing = ENTRIES.get(i);
                if (existing.modId() == modId) {
                    ENTRIES.set(i, new Installed(modId, file.fileId(), fileName,
                            file.displayName() == null ? existing.displayName() : file.displayName(),
                            namespace == null ? existing.namespace() : namespace,
                            file.date(), System.currentTimeMillis()));
                    scheduleSave();
                    return;
                }
            }
            ENTRIES.add(new Installed(modId, file.fileId(), fileName, file.displayName(),
                    namespace, file.date(), System.currentTimeMillis()));
        }
        scheduleSave();
    }

    public static void remove(long modId) {
        ensureLoaded();
        synchronized (LOCK) {
            if (ENTRIES.removeIf(existing -> existing.modId() == modId)) {
                scheduleSave();
            }
        }
    }

    private static void ensureLoaded() {
        if (!LOADED.compareAndSet(false, true)) return;
        Path file = dataFile();
        if (file == null || !Files.isRegularFile(file)) return;
        synchronized (LOCK) {
            try {
                JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (!element.isJsonObject()) return;
                JsonElement entries = element.getAsJsonObject().get("entries");
                if (entries == null || !entries.isJsonArray()) return;
                for (JsonElement item : entries.getAsJsonArray()) {
                    if (!item.isJsonObject()) continue;
                    JsonObject object = item.getAsJsonObject();
                    long modId = longValue(object, "modId", -1L);
                    long fileId = longValue(object, "fileId", -1L);
                    if (modId < 0L || fileId <= 0L) continue;
                    ENTRIES.add(new Installed(modId, fileId, string(object, "fileName"),
                            string(object, "displayName"), string(object, "namespace"),
                            longValue(object, "fileDate", 0L), longValue(object, "installedAt", 0L)));
                }
            } catch (Throwable ignored) {
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
        Path file = dataFile();
        if (file == null) return;
        String raw;
        synchronized (LOCK) {
            JsonObject root = new JsonObject();
            root.addProperty("version", FORMAT_VERSION);
            JsonArray entries = new JsonArray();
            for (Installed entry : ENTRIES) {
                JsonObject object = new JsonObject();
                object.addProperty("modId", entry.modId());
                object.addProperty("fileId", entry.fileId());
                object.addProperty("fileName", entry.fileName());
                object.addProperty("displayName", entry.displayName());
                object.addProperty("namespace", entry.namespace());
                object.addProperty("fileDate", entry.fileDate());
                object.addProperty("installedAt", entry.installedAt());
                entries.add(object);
            }
            root.add("entries", entries);
            raw = GSON.toJson(root);
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, raw, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException atomicMoveFailed) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Path dataFile() {
        try {
            return TaczFixesCache.file(FILE_NAME);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try {
            JsonElement element = object.get(key);
            return element == null || !element.isJsonPrimitive() ? fallback : element.getAsLong();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String string(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            if (element == null || !element.isJsonPrimitive()) return "";
            String value = element.getAsString();
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }
}
