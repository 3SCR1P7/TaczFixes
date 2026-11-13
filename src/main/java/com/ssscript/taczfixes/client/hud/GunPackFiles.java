package com.ssscript.taczfixes.client.hud;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 枪包文件访问工具: 只读取实例目录 tacz/<pack>/ 与 tacz/<pack>.zip 内的资源,
 * 不依赖 Minecraft ResourceManager, 因此枪包增删后无需等待资源包重载。
 */
public final class GunPackFiles {

    /** 单个被复制文件的大小上限(AUI 自身的图片上限为 8 MiB, 这里留出余量)。 */
    private static final long MAX_COPY_BYTES = 32L * 1024L * 1024L;

    private GunPackFiles() {
    }

    /** 按名称排序的枪包根目录/压缩包列表。 */
    public static List<Path> roots() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return List.of();
        Path taczDir = minecraft.gameDirectory.toPath().resolve("tacz");
        if (!Files.isDirectory(taczDir)) return List.of();
        try (Stream<Path> children = Files.list(taczDir)) {
            return children.filter(path -> Files.isDirectory(path) || isZip(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        } catch (IOException ignored) {
            return List.of();
        }
    }

    /** 判断枪包内是否存在指定相对路径的文件。 */
    public static boolean exists(String relative) {
        if (!isSafeRelative(relative)) return false;
        for (Path root : roots()) {
            try {
                if (Files.isDirectory(root)) {
                    if (Files.isRegularFile(root.resolve(relative))) return true;
                } else if (isZip(root)) {
                    try (ZipFile zip = new ZipFile(root.toFile())) {
                        ZipEntry entry = zip.getEntry(relative);
                        if (entry != null && !entry.isDirectory()) return true;
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return false;
    }

    /** 读取第一个匹配的文本资源, 文件不存在或读取失败返回 null。 */
    public static String readString(String relative) {
        if (!isSafeRelative(relative)) return null;
        for (Path root : roots()) {
            try {
                if (Files.isDirectory(root)) {
                    Path file = root.resolve(relative);
                    if (Files.isRegularFile(file)) {
                        return Files.readString(file, StandardCharsets.UTF_8);
                    }
                } else if (isZip(root)) {
                    try (ZipFile zip = new ZipFile(root.toFile())) {
                        ZipEntry entry = zip.getEntry(relative);
                        if (entry != null && !entry.isDirectory()) {
                            try (InputStream in = zip.getInputStream(entry)) {
                                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    /** 列出所有枪包中存在的 assets/<namespace>/hud 前缀。 */
    public static List<String> hudPrefixes() {
        Set<String> prefixes = new TreeSet<>();
        for (Path root : roots()) {
            try {
                if (Files.isDirectory(root)) {
                    Path assets = root.resolve("assets");
                    if (!Files.isDirectory(assets)) continue;
                    try (Stream<Path> namespaces = Files.list(assets)) {
                        namespaces.filter(Files::isDirectory).forEach(namespace -> {
                            Path hud = namespace.resolve("hud");
                            if (Files.isDirectory(hud)) {
                                prefixes.add("assets/" + namespace.getFileName() + "/hud");
                            }
                        });
                    }
                } else if (isZip(root)) {
                    try (ZipFile zip = new ZipFile(root.toFile())) {
                        Enumeration<? extends ZipEntry> entries = zip.entries();
                        while (entries.hasMoreElements()) {
                            String name = entries.nextElement().getName();
                            String[] parts = name.split("/");
                            if (parts.length >= 3 && "assets".equals(parts[0]) && "hud".equals(parts[2])) {
                                String namespace = parts[1];
                                if (isSafeSegment(namespace)) {
                                    prefixes.add("assets/" + namespace + "/hud");
                                }
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return new ArrayList<>(prefixes);
    }

    /**
     * 把所有枪包中 prefix 目录下的文件复制到 destination, 后加载的枪包覆盖先加载的。
     *
     * @return 实际复制的文件数量
     */
    public static int copyTree(String prefix, Path destination) {
        if (!isSafeRelative(prefix) || destination == null) return 0;
        int copied = 0;
        for (Path root : roots()) {
            try {
                if (Files.isDirectory(root)) {
                    Path source = root.resolve(prefix);
                    if (!Files.isDirectory(source)) continue;
                    List<Path> files;
                    try (Stream<Path> walk = Files.walk(source)) {
                        files = walk.filter(Files::isRegularFile)
                                .sorted()
                                .collect(Collectors.toList());
                    }
                    for (Path file : files) {
                        String relative = source.relativize(file).toString().replace('\\', '/');
                        if (!isSafeRelative(relative)) continue;
                        Path target = destination.resolve(relative).normalize();
                        if (!target.startsWith(destination.normalize())) continue;
                        try {
                            Files.createDirectories(target.getParent());
                            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                            copied++;
                        } catch (IOException ignored) {
                        }
                    }
                } else if (isZip(root)) {
                    try (ZipFile zip = new ZipFile(root.toFile())) {
                        List<? extends ZipEntry> entries;
                        try (Stream<? extends ZipEntry> stream = zip.stream()) {
                            entries = stream
                                    .filter(entry -> !entry.isDirectory()
                                            && entry.getName().startsWith(prefix + "/"))
                                    .sorted(Comparator.comparing(ZipEntry::getName))
                                    .collect(Collectors.toList());
                        }
                        for (ZipEntry entry : entries) {
                            if (entry.getSize() > MAX_COPY_BYTES) continue;
                            String relative = entry.getName().substring(prefix.length() + 1);
                            if (!isSafeRelative(relative)) continue;
                            Path target = destination.resolve(relative).normalize();
                            if (!target.startsWith(destination.normalize())) continue;
                            try (InputStream in = zip.getInputStream(entry)) {
                                Files.createDirectories(target.getParent());
                                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                                copied++;
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return copied;
    }

    /** 删除目录树(仅用于本模组管理的 apricity/taczfixes 目录)。 */
    public static void deleteTree(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    public static boolean isSafeRelative(String path) {
        if (path == null || path.isEmpty()) return false;
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.endsWith("/")) return false;
        for (String part : normalized.split("/")) {
            if (!isSafeSegment(part)) return false;
        }
        return true;
    }

    private static boolean isSafeSegment(String segment) {
        return segment != null && !segment.isEmpty()
                && !".".equals(segment) && !"..".equals(segment)
                && !segment.startsWith(".");
    }

    private static boolean isZip(Path path) {
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip");
    }
}
