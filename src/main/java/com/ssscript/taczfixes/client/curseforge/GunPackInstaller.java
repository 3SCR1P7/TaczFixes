package com.ssscript.taczfixes.client.curseforge;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 下载 / 校验 / 安装 TaCZ 枪包。
 *
 * TaCZ 的加载规则(见 TaCZ GunPackLoader.fromZipPath): zip 根目录必须存在 gunpack.meta.json,
 * 否则整个枪包会被 TaCZ 跳过。因此安装前用同样的规则做校验, 避免“提示安装成功但枪包列表里没有”。
 * 下载先写游戏目录下的临时文件夹, 校验通过后再移动到 tacz/ 中, 避免半截文件被 TaCZ 扫描到。
 */
public final class GunPackInstaller {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long STALL_TIMEOUT_MS = 120_000L;
    private static final long STALE_TEMP_MILLIS = 24L * 60L * 60L * 1000L;
    private static final String TEMP_DIR_NAME = ".taczfixes-tmp";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "taczfixes-download-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private GunPackInstaller() {
    }

    /** 下载的文件不是可加载的枪包时抛出(调用方可以改试其它下载地址)。 */
    public static class InvalidGunPackException extends IOException {
        private final boolean sha1Verified;

        public InvalidGunPackException(String detail, boolean sha1Verified) {
            super(detail);
            this.sha1Verified = sha1Verified;
        }

        /** sha1 已与 CurseForge 给出的值一致, 说明所有镜像拿到的文件相同, 没必要再换地址重试。 */
        public boolean isSha1Verified() {
            return this.sha1Verified;
        }
    }

    /** 下载失败(HTTP 状态/网络错误)时抛出, message 会显示在界面上。 */
    public static class DownloadException extends IOException {
        public DownloadException(String message) {
            super(message);
        }
    }

    /** 一次安装的结果。 */
    public record InstallResult(Path file, boolean converted, boolean metaAdded) {
    }

    public static InstallResult downloadAndInstall(String url, String fileName, Path taczDir,
                                                   DoubleConsumer progress, double timeoutSeconds) throws IOException {
        return downloadAndInstall(url, fileName, taczDir, progress, timeoutSeconds, null);
    }

    /**
     * 下载 url 到临时文件, 校验后安装为 taczDir 下的新枪包。
     * 若下载到的是旧版格式(或新版布局但缺 gunpack.meta.json), 会先自动转换/补全再安装。
     *
     * @param progress     0..1 的下载进度回调(后台线程)
     * @param expectedSha1 期望的 SHA-1(可为 null)
     * @return 安装结果(包含是否做过格式转换)
     */
    public static InstallResult downloadAndInstall(String url, String fileName, Path taczDir,
                                                   DoubleConsumer progress, double timeoutSeconds,
                                                   String expectedSha1) throws IOException {
        if (url == null || url.isBlank()) throw new DownloadException("empty download url");
        final URI uri;
        final HttpRequest request;
        try {
            uri = URI.create(url.trim());
            request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis((long) (timeoutSeconds * 1000.0D)))
                    .header("User-Agent", "taczfixes/2.1.1 (Minecraft-Forge-1.20.1; gunpack-browser)")
                    .header("Accept", "application/zip,application/octet-stream,*/*")
                    .GET()
                    .build();
        } catch (RuntimeException invalidUrl) {
            throw new DownloadException("invalid download url: " + invalidUrl.getMessage());
        }

        Files.createDirectories(taczDir);
        Path tempDir = taczDir.resolveSibling(TEMP_DIR_NAME);
        Files.createDirectories(tempDir);
        cleanupStaleTemps(tempDir);
        Path temp = tempDir.resolve(UUID.randomUUID() + ".tmp");
        boolean success = false;
        boolean converted = false;
        boolean metaAdded = false;
        try {
            HttpResponse<InputStream> response;
            try {
                response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new DownloadException("cancelled");
            } catch (IOException network) {
                throw new DownloadException(network.getClass().getSimpleName() + ": " + network.getMessage());
            }
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                try (InputStream ignored = response.body()) {
                    // 关闭响应体
                }
                throw new DownloadException("HTTP " + status);
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (contentType.toLowerCase(Locale.ROOT).startsWith("text/")) {
                try (InputStream ignored = response.body()) {
                    // 关闭响应体
                }
                throw new DownloadException("unexpected content type: " + contentType);
            }
            long total = response.headers().firstValueAsLong("content-length").orElse(-1L);
            MessageDigest digest = expectedSha1 == null || expectedSha1.isBlank()
                    ? null : MessageDigest.getInstance("SHA-1");
            long read = 0L;
            final InputStream body = response.body();
            AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());
            AtomicBoolean stalled = new AtomicBoolean(false);
            ScheduledFuture<?> watchdog = WATCHDOG.scheduleAtFixedRate(() -> {
                if (System.currentTimeMillis() - lastActivity.get() > STALL_TIMEOUT_MS) {
                    stalled.set(true);
                    try {
                        body.close();
                    } catch (IOException ignored) {
                    }
                }
            }, 15L, 15L, TimeUnit.SECONDS);
            try (InputStream in = body;
                 var out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    if (digest != null) digest.update(buffer, 0, n);
                    read += n;
                    lastActivity.set(System.currentTimeMillis());
                    if (total > 0L) {
                        progress.accept(Math.min(1.0D, (double) read / (double) total));
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        throw new DownloadException("cancelled");
                    }
                }
            } finally {
                watchdog.cancel(false);
            }
            if (stalled.get()) throw new DownloadException("download stalled");
            if (read <= 0L) throw new DownloadException("empty file");
            if (total > 0L && read != total) {
                throw new DownloadException("incomplete download: " + read + "/" + total);
            }
            if (digest != null) {
                String actual = HexFormat.of().formatHex(digest.digest());
                if (!actual.equalsIgnoreCase(expectedSha1.trim())) {
                    throw new DownloadException("sha1 mismatch");
                }
            }
            if (!isValidGunPack(temp)) {
                // 旧版格式(1.1.x 及更早) / 新版布局但漏放 meta: 自动转换, 与 /tacz convert 一致
                try {
                    GunPackFormatConverter.ConversionResult conversion =
                            GunPackFormatConverter.ensureNewFormat(temp, tempDir);
                    if (conversion.file() != null && !conversion.file().equals(temp)) {
                        Files.deleteIfExists(temp);
                        temp = conversion.file();
                    }
                    converted = conversion.converted();
                    metaAdded = conversion.metaAdded();
                } catch (IOException conversionFailed) {
                    throw new InvalidGunPackException(
                            "not a gun pack and legacy conversion failed: " + conversionFailed.getMessage(),
                            digest != null);
                }
                if (!isValidGunPack(temp)) {
                    throw new InvalidGunPackException("converted archive is still not a valid gun pack",
                            digest != null);
                }
            }
            progress.accept(1.0D);
            Path target = uniqueTarget(taczDir, fileName);
            moveIntoPlace(temp, target);
            success = true;
            return new InstallResult(target, converted, metaAdded);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new DownloadException("sha1 unavailable");
        } finally {
            if (!success) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (IOException atomicFailed) {
            // 某些文件系统/杀软占用不支持原子移动, 退回普通复制
        }
        try {
            Files.copy(temp, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(temp);
        } catch (IOException copyFailed) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
            throw copyFailed;
        }
    }

    private static void cleanupStaleTemps(Path tempDir) {
        long cutoff = System.currentTimeMillis() - STALE_TEMP_MILLIS;
        try (Stream<Path> stream = Files.list(tempDir)) {
            stream.forEach(path -> {
                try {
                    if (Files.isRegularFile(path) && Files.getLastModifiedTime(path).toMillis() < cutoff) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    /**
     * TaCZ 的加载规则: zip 根目录必须有 gunpack.meta.json 且内容是可解析的 JSON 对象。
     * 其它形式(多了外层文件夹 / 只有 assets/ns/gunpack_info.json)TaCZ 会跳过, 这里也判为无效。
     */
    public static boolean isValidGunPack(Path archive) {
        if (archive == null || !Files.isRegularFile(archive)) return false;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry meta = zip.getEntry("gunpack.meta.json");
            if (meta == null || meta.isDirectory()) return false;
            if (meta.getSize() > 64L * 1024L) return false;
            try (InputStream stream = zip.getInputStream(meta)) {
                // 限制读取量, 避免伪造的超大 meta.json 造成 OOM
                byte[] data = stream.readNBytes(64 * 1024);
                if (data.length >= 64 * 1024) return false;
                String text = new String(data, StandardCharsets.UTF_8).trim();
                if (text.isEmpty()) return false;
                JsonElement element = JsonParser.parseString(text);
                if (element == null || !element.isJsonObject()) return false;
                // TaCZ 用 meta 里的 namespace 作为枪包名, 空 namespace 会导致名字为 null
                JsonElement namespace = element.getAsJsonObject().get("namespace");
                if (namespace == null || !namespace.isJsonPrimitive()) return false;
                String value = namespace.getAsString();
                return value != null && !value.isBlank();
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 读取已安装枪包 gunpack.meta.json 里的 namespace(用于把它标记为“未启用/可用”),
     * 读取失败返回 null。
     */
    public static String readGunPackNamespace(Path archive) {
        if (archive == null || !Files.isRegularFile(archive)) return null;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry meta = zip.getEntry("gunpack.meta.json");
            if (meta == null || meta.isDirectory() || meta.getSize() > 64L * 1024L) return null;
            try (InputStream stream = zip.getInputStream(meta)) {
                byte[] data = stream.readNBytes(64 * 1024);
                if (data.length >= 64 * 1024) return null;
                JsonElement element = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
                if (element == null || !element.isJsonObject()) return null;
                JsonElement namespace = element.getAsJsonObject().get("namespace");
                if (namespace == null || !namespace.isJsonPrimitive()) return null;
                String value = namespace.getAsString();
                return value == null || value.isBlank() ? null : value;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 去掉文件名里的非法字符, 保证是 .zip 名称。 */
    public static String safeFileName(String raw) {
        String name = raw == null ? "" : raw.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        while (name.endsWith(".") || name.endsWith(" ")) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.isBlank()) name = "gun-pack.zip";
        if (name.length() > 180) name = name.substring(0, 176);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name + ".zip";
        // Windows 保留设备名不能作为文件名(检查第一个点之前的部分, 如 aux.tar.zip 也非法)
        int firstDot = name.indexOf('.');
        String stem = (firstDot > 0 ? name.substring(0, firstDot) : name).trim().toUpperCase(Locale.ROOT);
        if (stem.equals("CON") || stem.equals("PRN") || stem.equals("AUX") || stem.equals("NUL")
                || stem.matches("COM[1-9]") || stem.matches("LPT[1-9]")) {
            name = "_" + name;
        }
        return name;
    }

    /** 目标目录里已存在同名文件时追加 (1)/(2)... */
    public static Path uniqueTarget(Path dir, String fileName) {
        String safe = safeFileName(fileName);
        Path target = dir.resolve(safe);
        if (!Files.exists(target)) return target;
        int dot = safe.lastIndexOf('.');
        String stem = dot > 0 ? safe.substring(0, dot) : safe;
        String ext = dot > 0 ? safe.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            Path candidate = dir.resolve(stem + " (" + i + ")" + ext);
            if (!Files.exists(candidate)) return candidate;
        }
        return dir.resolve(stem + "-" + System.currentTimeMillis() + ext);
    }

    /**
     * 更新安装收尾: 尽量让新包占用 CurseForge 给出的文件名, 并删除旧版本 jar。
     *
     * @param oldFile       旧版本 jar(安装记录里的文件名), 可为 null
     * @param installedFile 本次下载安装得到的文件
     * @param desiredName   CurseForge 文件列表里的文件名
     * @return 最终落盘的文件
     */
    public static Path replaceInstalled(Path taczDir, Path oldFile, Path installedFile, String desiredName) {
        if (installedFile == null) return oldFile;
        Path desired = taczDir.resolve(safeFileName(desiredName));
        Path result = installedFile;
        try {
            // 仅当目标名正好是旧版本文件时才原地替换(避免误删其它枪包)
            if (!installedFile.equals(desired) && oldFile != null && desired.equals(oldFile)) {
                Files.deleteIfExists(desired);
                Files.move(installedFile, desired, StandardCopyOption.REPLACE_EXISTING);
                result = desired;
            }
        } catch (IOException ignored) {
            result = installedFile;
        }
        try {
            if (oldFile != null && !oldFile.equals(result) && Files.isRegularFile(oldFile)) {
                Files.deleteIfExists(oldFile);
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    /** 人类可读的文件大小。 */
    public static String formatSize(long bytes) {
        if (bytes < 0L) return "?";
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0D);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0D * 1024.0D));
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0D * 1024.0D * 1024.0D));
    }
}
