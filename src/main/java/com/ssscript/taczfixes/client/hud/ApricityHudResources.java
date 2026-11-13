package com.ssscript.taczfixes.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 把枪包内 {@code assets/<namespace>/hud/**} 复制到实例目录
 * {@code <gameDir>/apricity/taczfixes/<namespace>/hud/**},
 * 让 ApricityUI 的资源加载器可以按逻辑路径 {@code taczfixes/...} 找到它们。
 *
 * <p>该目录完全由本模组管理: 每次资源重载会先整体删除再重建, 不会残留已删除枪包的文件。
 * 玩家/整合包不应手动往这里放文件。</p>
 */
public final class ApricityHudResources {

    public static final String LOGICAL_ROOT = "taczfixes";
    private static final String HUD_PREFIX = "assets/";
    private static final String HUD_SUFFIX = "/hud";

    private static final Set<String> ATTEMPTED_NAMESPACES = new HashSet<>();
    private static volatile List<String> syncedHtml = List.of();

    private ApricityHudResources() {
    }

    public static Path managedRoot() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("apricity")
                .resolve(LOGICAL_ROOT);
    }

    public static String logicalPath(ResourceLocation id) {
        return LOGICAL_ROOT + "/" + id.getNamespace() + "/hud/" + id.getPath() + ".html";
    }

    public static List<String> syncedHtmlPaths() {
        return syncedHtml;
    }

    public static synchronized void clearAttempts() {
        ATTEMPTED_NAMESPACES.clear();
    }

    /**
     * 资源重载时调用(可在后台线程执行): 清空托管目录, 重新复制所有枪包 HUD 资源。
     * AUI 模板的实际注册在客户端线程由 {@link #reloadAll(ApricityHudBackend)} 完成。
     */
    public static synchronized void syncAllFiles() {
        if (!ApricityHudSupport.isAuiModLoaded()) return;
        Path root = managedRoot();
        GunPackFiles.deleteTree(root);
        for (String prefix : GunPackFiles.hudPrefixes()) {
            String namespace = namespaceOf(prefix);
            if (namespace == null) continue;
            GunPackFiles.copyTree(prefix, root.resolve(namespace).resolve("hud"));
        }
        syncedHtml = collectHtml(root, LOGICAL_ROOT);
        ATTEMPTED_NAMESPACES.clear();
    }

    /** 客户端线程: 重新注册所有已复制页面的 AUI 模板。 */
    public static void reloadAll(ApricityHudBackend backend) {
        if (backend == null) return;
        backend.reloadTemplates(syncedHtml);
    }

    /**
     * 按需补齐某个命名空间的 HUD 资源(例如运行中添加了枪包但没有触发资源重载)。
     * 只在客户端线程调用。
     */
    public static synchronized boolean ensure(ResourceLocation id, ApricityHudBackend backend) {
        if (id == null || backend == null) return false;
        Path root = managedRoot();
        Path target = root.resolve(id.getNamespace()).resolve("hud")
                .resolve(id.getPath() + ".html").normalize();
        if (!target.startsWith(root.normalize())) return false;
        if (Files.isRegularFile(target)) return true;
        if (!ATTEMPTED_NAMESPACES.add(id.getNamespace())) return false;
        int copied = GunPackFiles.copyTree("assets/" + id.getNamespace() + "/hud",
                root.resolve(id.getNamespace()).resolve("hud"));
        if (copied <= 0) return false;
        backend.reloadTemplates(collectHtml(root.resolve(id.getNamespace()).resolve("hud"),
                LOGICAL_ROOT + "/" + id.getNamespace() + "/hud"));
        return Files.isRegularFile(target);
    }

    private static String namespaceOf(String prefix) {
        if (prefix == null || !prefix.startsWith(HUD_PREFIX) || !prefix.endsWith(HUD_SUFFIX)) return null;
        String namespace = prefix.substring(HUD_PREFIX.length(),
                prefix.length() - HUD_SUFFIX.length());
        return GunPackFiles.isSafeRelative(namespace) ? namespace : null;
    }

    private static List<String> collectHtml(Path directory, String logicalPrefix) {
        if (directory == null || !Files.isDirectory(directory)) return List.of();
        try (Stream<Path> walk = Files.walk(directory)) {
            List<String> result = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".html"))
                    .sorted()
                    .map(path -> logicalPrefix + "/" + directory.relativize(path)
                            .toString().replace('\\', '/'))
                    .collect(Collectors.toList());
            return new ArrayList<>(result);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
