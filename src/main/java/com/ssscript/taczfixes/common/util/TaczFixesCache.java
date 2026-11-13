package com.ssscript.taczfixes.common.util;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 本模组的缓存/数据目录: 游戏目录下的 taczfixes_cache。
 * 枪械/配件 data 修改、改装方案、CurseForge 搜索缓存与下载记录都放在这里, 不再写入 config。
 * 旧版本 config/taczfixes 下的对应文件在首次访问时自动迁移过来。
 */
public final class TaczFixesCache {
    private static final String ROOT_NAME = "taczfixes_cache";
    private static final Set<String> MIGRATED_FILES = ConcurrentHashMap.newKeySet();
    private static final Set<String> MIGRATED_DIRS = ConcurrentHashMap.newKeySet();

    private TaczFixesCache() {
    }

    /** 缓存根目录(由写入方负责创建)。 */
    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve(ROOT_NAME);
    }

    /** 缓存目录下的单个文件, 首次访问时从旧 config/taczfixes 位置迁移。 */
    public static Path file(String name) {
        Path target = root().resolve(name);
        if (MIGRATED_FILES.add(name)) {
            migrateFile(name, target);
        }
        return target;
    }

    /** 缓存目录下的子目录, 首次访问时从旧 config/taczfixes 位置迁移。 */
    public static Path dir(String name) {
        Path target = root().resolve(name);
        if (MIGRATED_DIRS.add(name)) {
            migrateDir(name, target);
        }
        return target;
    }

    private static Path legacyRoot() {
        Path configured = FMLPaths.CONFIGDIR.get().resolve("taczfixes");
        if (Files.isDirectory(configured)) {
            return configured;
        }
        Path gameDir = FMLPaths.GAMEDIR.get().resolve("config").resolve("taczfixes");
        return Files.isDirectory(gameDir) ? gameDir : configured;
    }

    private static void migrateFile(String name, Path target) {
        try {
            Path legacy = legacyRoot().resolve(name);
            if (!Files.isRegularFile(legacy)) {
                return;
            }
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Files.copy(legacy, target);
            }
            Files.deleteIfExists(legacy);
        } catch (Throwable ignored) {
            // 迁移失败时保留旧文件, 下次启动重试
        }
    }

    private static void migrateDir(String name, Path target) {
        try {
            Path legacy = legacyRoot().resolve(name);
            if (!Files.isDirectory(legacy)) {
                return;
            }
            Files.createDirectories(target);
            List<Path> sources;
            try (Stream<Path> stream = Files.list(legacy)) {
                sources = stream.toList();
            }
            for (Path source : sources) {
                try {
                    Path destination = target.resolve(source.getFileName().toString());
                    if (!Files.exists(destination)) {
                        Files.copy(source, destination);
                    }
                    Files.deleteIfExists(source);
                } catch (Throwable ignored) {
                }
            }
            try (Stream<Path> remaining = Files.list(legacy)) {
                if (remaining.findAny().isEmpty()) {
                    Files.deleteIfExists(legacy);
                }
            }
        } catch (Throwable ignored) {
            // 迁移失败时保留旧目录, 下次启动重试
        }
    }
}
