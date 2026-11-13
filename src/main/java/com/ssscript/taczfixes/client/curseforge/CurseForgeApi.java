package com.ssscript.taczfixes.client.curseforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;

/**
 * CurseForge 枪包搜索/下载接口。
 *
 * 传输通道: 默认走国内可直连的 CurseForge 镜像(无需 API Key); 如果在设置里填写了个人 API Key
 * 并把 API 地址改成 https://api.curseforge.com/v1, 则走官方 API。
 * Key 只保存在本地 config/taczfixes_curseforge.json, 绝不打包进 mod。
 *
 * 枪包在 CurseForge 的分类是 classId=4546 (Customization), 不是 Mods(6)/Resource Packs(12)。
 */
public final class CurseForgeApi {
    public static final int GAME_ID = 432;
    public static final int GUN_PACK_CLASS_ID = 4546;
    /** TaCZ 主 mod 在 CurseForge 的项目 id, 枪包文件通常依赖它。 */
    public static final int TACZ_MOD_ID = 1028108;
    public static final String GAME_VERSION = "1.20.1";
    /**
     * 每页请求数量(CurseForge 上限 50)。20 条时响应体约 115KB / 延迟约 4s,
     * 50 条时约 260KB / 8s; 为了搜索速度取 20, 过滤无关结果后一页仍够用。
     */
    public static final int PAGE_SIZE = 20;
    /** 默认搜索关键词: 能覆盖绝大部分 TaCZ 枪包。 */
    public static final String DEFAULT_QUERY = "tacz";
    private static final String USER_AGENT = "taczfixes/2.1.1 (Minecraft-Forge-1.20.1; gunpack-browser)";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final double DOWNLOAD_TIMEOUT_SECONDS = 60.0D * 30.0D;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "taczfixes-curseforge");
        thread.setDaemon(true);
        return thread;
    });

    /** 提前在后台加载磁盘缓存, 打开 CurseForge 界面时调用。 */
    public static void warmUpCache() {
        CurseForgeCache.warmUp();
    }

    private CurseForgeApi() {
    }

    /** CurseForge 请求失败。message 可直接显示给玩家。 */
    public static class CurseForgeException extends RuntimeException {
        public CurseForgeException(String message) {
            super(message);
        }

        public CurseForgeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record ModInfo(long id, String name, String slug, String summary, String author,
                          String logoUrl, long downloads, long modified, boolean allowModDistribution,
                          boolean taczGunPack) {
    }

    public record FileInfo(long fileId, long modId, String fileName, String displayName, long length,
                           long date, String downloadUrl, String sha1, List<String> gameVersions,
                           int releaseType, boolean available) {
    }

    public record SearchPage(List<ModInfo> mods, int totalCount, int pageIndex) {
    }

    /** 搜索结果排序方式。 */
    public enum SortMode {
        DOWNLOADS(6, "desc", "gui.taczfixes.curseforge.sort.downloads"),
        UPDATED(3, "desc", "gui.taczfixes.curseforge.sort.updated"),
        NAME(4, "asc", "gui.taczfixes.curseforge.sort.name");

        private final int field;
        private final String order;
        private final String labelKey;

        SortMode(int field, String order, String labelKey) {
            this.field = field;
            this.order = order;
            this.labelKey = labelKey;
        }

        public int field() {
            return this.field;
        }

        public String order() {
            return this.order;
        }

        public String labelKey() {
            return this.labelKey;
        }

        public SortMode next() {
            SortMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        public static SortMode byName(String name) {
            if (name != null) {
                for (SortMode mode : values()) {
                    if (mode.name().equalsIgnoreCase(name)) return mode;
                }
            }
            return DOWNLOADS;
        }
    }

    // ------------------------------------------------------------------ search

    public static CompletableFuture<SearchPage> search(String query, int pageIndex) {
        return search(query, pageIndex, SortMode.DOWNLOADS);
    }

    /** 搜索枪包; query 为空时使用默认关键词 "tacz"。pageIndex 从 0 开始。命中内存缓存时立即返回。 */
    public static CompletableFuture<SearchPage> search(String query, int pageIndex, SortMode sortMode) {
        String filter = query == null || query.isBlank() ? DEFAULT_QUERY : query.trim();
        final int page = Math.max(0, pageIndex);
        final SortMode sort = sortMode == null ? SortMode.DOWNLOADS : sortMode;
        SearchPage cached = cachedSearch(filter, page, sort);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        String key = searchCacheKey(filter, page, sort);
        synchronized (IN_FLIGHT) {
            CompletableFuture<SearchPage> running = IN_FLIGHT.get(key);
            if (running != null) {
                // 预取中的同一页请求直接复用, 避免翻页时重复发请求
                return running;
            }
            CompletableFuture<SearchPage> future = fetchSearch(filter, page, sort);
            IN_FLIGHT.put(key, future);
            future.whenComplete((result, error) -> {
                synchronized (IN_FLIGHT) {
                    IN_FLIGHT.remove(key);
                }
                if (result != null) {
                    cacheSearch(filter, page, sort, result);
                }
            });
            return future;
        }
    }

    private static CompletableFuture<SearchPage> fetchSearch(String filter, int page, SortMode sort) {
        String params = "gameId=" + GAME_ID
                + "&classId=" + GUN_PACK_CLASS_ID
                + "&gameVersion=" + encodeQuery(GAME_VERSION)
                + "&searchFilter=" + encodeQuery(filter)
                + "&sortField=" + sort.field()
                + "&sortOrder=" + sort.order()
                + "&index=" + (page * PAGE_SIZE)
                + "&pageSize=" + PAGE_SIZE;
        final String cacheKey = searchCacheKey(filter, page, sort);
        return getJson("/mods/search", params).thenApply(root -> {
            SearchPage parsed = parseSearchPage(root, page);
            // 只缓存成功响应; 落盘在后台线程完成
            CurseForgeCache.put(cacheKey, root);
            return parsed;
        });
    }

    private static SearchPage parseSearchPage(JsonObject root, int page) {
        List<ModInfo> mods = new ArrayList<>();
        JsonArray data = array(root, "data");
        if (data != null) {
            for (JsonElement element : data) {
                if (!element.isJsonObject()) continue;
                ModInfo info = parseMod(element.getAsJsonObject());
                if (info != null) mods.add(info);
            }
        }
        int total = intValue(object(root, "pagination"), "totalCount",
                mods.size() == PAGE_SIZE ? (page + 2) * PAGE_SIZE : page * PAGE_SIZE + mods.size());
        return new SearchPage(mods, total, page);
    }

    // 搜索结果内存缓存(不落盘): 翻页/切排序/重开界面时直接复用, 显著降低等待。
    private static final long SEARCH_CACHE_TTL_MS = 5L * 60L * 1000L;
    private static final int SEARCH_CACHE_MAX = 16;
    private static final java.util.Map<String, CachedSearch> SEARCH_CACHE =
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, CachedSearch> eldest) {
                    return size() > SEARCH_CACHE_MAX;
                }
            };
    /** 正在进行的搜索请求, 用于合并预取与用户翻页的重复请求。 */
    private static final java.util.Map<String, CompletableFuture<SearchPage>> IN_FLIGHT = new java.util.HashMap<>();
    /** 正在进行的文件列表请求, 用于合并自动更新检查与手动查看。 */
    private static final java.util.Map<String, CompletableFuture<List<FileInfo>>> FILES_IN_FLIGHT = new java.util.HashMap<>();

    private record CachedSearch(SearchPage page, long timestamp) {
    }

    private static String searchCacheKey(String filter, int page, SortMode sort) {
        // 带上 API 地址和游戏版本: 设置里切换镜像/官方 API 后不会命中旧缓存
        return "search|" + CurseForgeConfig.apiBase() + '|' + GAME_VERSION + '|' + sort.name()
                + '|' + page + '|' + filter;
    }

    private static SearchPage cachedSearch(String filter, int page, SortMode sort) {
        String key = searchCacheKey(filter, page, sort);
        synchronized (SEARCH_CACHE) {
            CachedSearch cached = SEARCH_CACHE.get(key);
            if (cached != null) {
                if (System.currentTimeMillis() - cached.timestamp() <= SEARCH_CACHE_TTL_MS) {
                    return cached.page();
                }
                SEARCH_CACHE.remove(key);
            }
        }
        // 内存未命中时尝试磁盘缓存, 命中则解析后立即返回(不再发网络请求)
        JsonObject payload = CurseForgeCache.get(key);
        if (payload == null) return null;
        SearchPage parsed;
        try {
            parsed = parseSearchPage(payload, page);
        } catch (Throwable ignored) {
            return null;
        }
        synchronized (SEARCH_CACHE) {
            SEARCH_CACHE.put(key, new CachedSearch(parsed, System.currentTimeMillis()));
        }
        return parsed;
    }

    private static void cacheSearch(String filter, int page, SortMode sort, SearchPage result) {
        synchronized (SEARCH_CACHE) {
            SEARCH_CACHE.put(searchCacheKey(filter, page, sort),
                    new CachedSearch(result, System.currentTimeMillis()));
        }
    }

    private static ModInfo parseMod(JsonObject data) {
        long id = longValue(data, "id", -1L);
        if (id < 0L) return null;
        String name = string(data, "name", "?");
        String slug = string(data, "slug", "");
        String summary = string(data, "summary", "");
        String author = "";
        JsonArray authors = array(data, "authors");
        if (authors != null && authors.size() > 0 && authors.get(0).isJsonObject()) {
            author = string(authors.get(0).getAsJsonObject(), "name", "");
        }
        String logo = "";
        JsonObject logoObject = object(data, "logo");
        if (logoObject != null) {
            logo = string(logoObject, "thumbnailUrl", "");
        }
        long downloads = longValue(data, "downloadCount", 0L);
        long modified = parseDate(string(data, "dateModified", null));
        boolean allowDistribution = true;
        if (data.has("allowModDistribution") && data.get("allowModDistribution").isJsonPrimitive()) {
            try {
                allowDistribution = data.get("allowModDistribution").getAsBoolean();
            } catch (Throwable ignored) {
                allowDistribution = true;
            }
        }
        boolean taczGunPack = isTaczGunPack(data, name, slug, summary);
        return new ModInfo(id, name, slug, summary, author, logo, downloads, modified,
                allowDistribution, taczGunPack);
    }

    /**
     * 判断一个搜索结果是否真的是 TaCZ 枪包, 排掉 shader/幸运方块等无关项目。
     * 依据(任一即可):
     * 1. 最新文件依赖 TaCZ 主 mod(modId=1028108);
     * 2. 最新文件包含 gunpack.meta.json 模块(只有枪包 zip 才有);
     * 3. 名称/简介/slug 里出现 tacz / timeless / gunpack / gun pack / 枪包。
     */
    private static boolean isTaczGunPack(JsonObject data, String name, String slug, String summary) {
        JsonArray latestFiles = array(data, "latestFiles");
        if (latestFiles != null) {
            for (JsonElement fileElement : latestFiles) {
                if (!fileElement.isJsonObject()) continue;
                JsonObject file = fileElement.getAsJsonObject();
                JsonArray dependencies = array(file, "dependencies");
                if (dependencies != null) {
                    for (JsonElement dependency : dependencies) {
                        if (!dependency.isJsonObject()) continue;
                        if (longValue(dependency.getAsJsonObject(), "modId", -1L) == TACZ_MOD_ID) {
                            return true;
                        }
                    }
                }
                JsonArray modules = array(file, "modules");
                if (modules != null) {
                    for (JsonElement module : modules) {
                        if (!module.isJsonObject()) continue;
                        String moduleName = string(module.getAsJsonObject(), "name", "");
                        if ("gunpack.meta.json".equals(moduleName) || "gunpack_info.json".equals(moduleName)) {
                            return true;
                        }
                    }
                }
            }
        }
        String text = (name + " " + slug + " " + summary).toLowerCase(Locale.ROOT);
        return text.contains("tacz") || text.contains("ta cz") || text.contains("timeless")
                || text.contains("gunpack") || text.contains("gun pack") || text.contains("枪包");
    }

    // ------------------------------------------------------------------- files

    /** 获取项目的 1.20.1 文件并按日期从新到旧排序。命中缓存(内存/磁盘)时立即返回。 */
    public static CompletableFuture<List<FileInfo>> listFiles(long modId) {
        return listFiles(modId, false);
    }

    /**
     * 获取项目的文件列表。
     *
     * @param forceRefresh true 时跳过 45 分钟磁盘缓存, 直接请求远端(仅自动更新检查使用);
     *                     响应仍会写回缓存, 不影响搜索结果缓存。
     */
    public static CompletableFuture<List<FileInfo>> listFiles(long modId, boolean forceRefresh) {
        String key = filesCacheKey(modId);
        if (!forceRefresh) {
            JsonObject cached = CurseForgeCache.get(key);
            if (cached != null) {
                try {
                    return CompletableFuture.completedFuture(parseFiles(cached, modId));
                } catch (Throwable ignored) {
                    // 缓存内容异常: 落回网络请求
                }
            }
        }
        synchronized (FILES_IN_FLIGHT) {
            CompletableFuture<List<FileInfo>> running = FILES_IN_FLIGHT.get(key);
            if (running != null) {
                return running;
            }
            CompletableFuture<List<FileInfo>> future = fetchFiles(modId, key);
            FILES_IN_FLIGHT.put(key, future);
            future.whenComplete((result, error) -> {
                synchronized (FILES_IN_FLIGHT) {
                    FILES_IN_FLIGHT.remove(key);
                }
            });
            return future;
        }
    }

    private static String filesCacheKey(long modId) {
        return "files|" + CurseForgeConfig.apiBase() + '|' + GAME_VERSION + '|' + modId;
    }

    private static CompletableFuture<List<FileInfo>> fetchFiles(long modId, String cacheKey) {
        String params = "gameVersion=" + encodeQuery(GAME_VERSION) + "&pageSize=50&index=0";
        return getJson("/mods/" + modId + "/files", params).thenApply(root -> {
            CurseForgeCache.put(cacheKey, root);
            return parseFiles(root, modId);
        });
    }

    private static List<FileInfo> parseFiles(JsonObject root, long modId) {
        List<FileInfo> files = new ArrayList<>();
        JsonArray data = array(root, "data");
        if (data != null) {
            for (JsonElement element : data) {
                if (!element.isJsonObject()) continue;
                FileInfo info = parseFile(element.getAsJsonObject(), modId);
                if (info != null) files.add(info);
            }
        }
        files.sort((a, b) -> Long.compare(b.date(), a.date()));
        return files;
    }

    private static FileInfo parseFile(JsonObject data, long modId) {
        long fileId = longValue(data, "id", -1L);
        if (fileId < 0L) return null;
        String fileName = string(data, "fileName", "");
        if (fileName.isBlank()) return null;
        String displayName = string(data, "displayName", fileName);
        long length = longValue(data, "fileLength", -1L);
        long date = parseDate(string(data, "fileDate", null));
        String downloadUrl = string(data, "downloadUrl", null);
        String sha1 = null;
        JsonArray hashes = array(data, "hashes");
        if (hashes != null) {
            for (JsonElement element : hashes) {
                if (!element.isJsonObject()) continue;
                JsonObject hash = element.getAsJsonObject();
                String algo = string(hash, "algo", "");
                if ("1".equals(algo) || "sha1".equalsIgnoreCase(algo)) {
                    sha1 = string(hash, "value", null);
                }
            }
        }
        List<String> gameVersions = new ArrayList<>();
        JsonArray versions = array(data, "gameVersions");
        if (versions != null) {
            for (JsonElement element : versions) {
                if (element.isJsonPrimitive()) gameVersions.add(element.getAsString());
            }
        }
        int releaseType = intValue(data, "releaseType", 0);
        boolean available = true;
        if (data.has("isAvailable") && data.get("isAvailable").isJsonPrimitive()) {
            try {
                available = data.get("isAvailable").getAsBoolean();
            } catch (Throwable ignored) {
                available = true;
            }
        }
        if (data.has("fileStatus") && data.get("fileStatus").isJsonPrimitive()) {
            int fileStatus = intValue(data, "fileStatus", 0);
            // 4 = 已处理可下载; 其它状态(如 1 上传中)视为不可用
            if (fileStatus != 0 && fileStatus != 4) available = false;
        }
        return new FileInfo(fileId, longValue(data, "modId", modId), fileName, displayName, length,
                date, downloadUrl == null || downloadUrl.isBlank() ? null : downloadUrl, sha1, gameVersions,
                releaseType, available);
    }

    /**
     * 从文件列表里挑最新的 zip 枪包文件: 优先可下载的正式版(releaseType=1),
     * 其次可下载的任何 zip, 最后才退回不可用的文件。找不到返回 null。
     */
    public static FileInfo pickLatestGunPackFile(List<FileInfo> files) {
        if (files == null) return null;
        FileInfo latestAvailableRelease = null;
        FileInfo latestAvailable = null;
        FileInfo latestUnavailable = null;
        for (FileInfo file : files) {
            String name = file.fileName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".zip")) continue;
            if (file.length() == 0L) continue;
            if (!file.available()) {
                if (latestUnavailable == null || file.date() > latestUnavailable.date()) latestUnavailable = file;
                continue;
            }
            if (latestAvailable == null || file.date() > latestAvailable.date()) latestAvailable = file;
            if (file.releaseType() == 1
                    && (latestAvailableRelease == null || file.date() > latestAvailableRelease.date())) {
                latestAvailableRelease = file;
            }
        }
        if (latestAvailableRelease != null) return latestAvailableRelease;
        if (latestAvailable != null) return latestAvailable;
        return latestUnavailable;
    }

    // ---------------------------------------------------------------- download

    /**
     * 下载并安装枪包(后台线程阻塞执行)。会依次尝试镜像 / API 直链 / 官方 CDN 等候选地址;
     * 某个地址返回的不是有效枪包时换下一个地址继续尝试。
     *
     * @param progress 0..1 下载进度, 后台线程调用
     */
    public static CompletableFuture<GunPackInstaller.InstallResult> downloadAndInstall(
            FileInfo file, Path taczDir, DoubleConsumer progress) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> candidates = new ArrayList<>(downloadCandidates(file));
            String resolved = resolveDownloadUrl(file);
            if (resolved != null && !candidates.contains(resolved)) {
                candidates.add(0, resolved);
            }
            if (candidates.isEmpty()) {
                throw new CurseForgeException("No download url for file " + file.fileName());
            }
            IOException lastIo = null;
            CurseForgeException lastError = null;
            boolean invalidPackSeen = false;
            for (String url : candidates) {
                try {
                    progress.accept(0.0D);
                    return GunPackInstaller.downloadAndInstall(url, file.fileName(), taczDir,
                            progress, DOWNLOAD_TIMEOUT_SECONDS, file.sha1());
                } catch (GunPackInstaller.InvalidGunPackException invalid) {
                    if (invalid.isSha1Verified()) {
                        // 文件内容与 CurseForge 记录的 sha1 一致: 换镜像也是同一个坏包, 直接报错
                        throw new CurseForgeException(
                                "downloaded file is not a gun pack (missing gunpack.meta.json): "
                                        + file.fileName(), invalid);
                    }
                    // 可能是镜像返回了 HTML 错误页: 换下一个地址继续
                    invalidPackSeen = true;
                    lastError = new CurseForgeException(
                            "downloaded file is not a gun pack (missing gunpack.meta.json): " + file.fileName(),
                            invalid);
                } catch (IOException io) {
                    lastIo = io;
                } catch (RuntimeException runtime) {
                    lastError = new CurseForgeException(runtime.getClass().getSimpleName()
                            + ": " + runtime.getMessage(), runtime);
                }
            }
            if (invalidPackSeen) {
                throw lastError == null
                        ? new CurseForgeException("downloaded file is not a gun pack: " + file.fileName())
                        : lastError;
            }
            if (lastError != null) throw lastError;
            throw new CurseForgeException(lastIo == null ? "download failed" : lastIo.getMessage(), lastIo);
        }, EXECUTOR);
    }

    /** 文件的所有候选下载地址(镜像优先, 其次 API 直链, 最后官方 CDN)。 */
    public static List<String> downloadCandidates(FileInfo file) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        long first = file.fileId() / 1000L;
        long second = file.fileId() % 1000L;
        String encodedName = encodePathSegment(file.fileName());
        String fileBase = CurseForgeConfig.fileBase();
        if (fileBase != null && !fileBase.isBlank()) {
            urls.add(trimTrailingSlash(fileBase) + "/" + first + "/" + second + "/" + encodedName);
        }
        if (file.downloadUrl() != null && !file.downloadUrl().isBlank()) {
            urls.add(file.downloadUrl().trim());
        }
        urls.add("https://edge.forgecdn.net/files/" + first + "/" + second + "/" + encodedName);
        urls.add("https://mediafilez.forgecdn.net/files/" + first + "/" + second + "/" + encodedName);
        return new ArrayList<>(urls);
    }

    /**
     * downloadUrl 为空(作者禁止第三方分发)时, 用官方/镜像的 download-url 端点取直链。
     * 失败返回 null, 调用方继续用 CDN 公式。
     */
    private static String resolveDownloadUrl(FileInfo file) {
        if (file.downloadUrl() != null && !file.downloadUrl().isBlank()) return null;
        try {
            JsonObject root = getJson("/mods/" + file.modId() + "/files/" + file.fileId() + "/download-url", "")
                    .get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            JsonElement data = root.get("data");
            if (data != null && data.isJsonPrimitive()) return data.getAsString();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {
        }
        return null;
    }

    // -------------------------------------------------------------- http utils

    private static CompletableFuture<JsonObject> getJson(String path, String params) {
        final HttpRequest request;
        final String url;
        try {
            String base = trimTrailingSlash(CurseForgeConfig.apiBase());
            url = base + path + (params == null || params.isBlank() ? "" : "?" + params);
            HttpRequest.Builder builder = HttpRequest.newBuilder(new URI(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .GET();
            String key = CurseForgeConfig.sanitizeKey(CurseForgeConfig.apiKey());
            if (!key.isEmpty()) {
                builder.header("x-api-key", key);
            }
            request = builder.build();
        } catch (Throwable error) {
            // 配置错误(地址非法等)不能把异常抛到渲染线程, 转成正常的失败结果
            return CompletableFuture.failedFuture(new CurseForgeException(
                    "Invalid CurseForge API config: " + error.getMessage(), error));
        }
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    int status = response.statusCode();
                    String body = response.body() == null ? "" : response.body();
                    if (status == 401 || status == 403) {
                        throw new CompletionException(new CurseForgeException(
                                "HTTP " + status + ": API Key invalid or missing"));
                    }
                    if (status == 404) {
                        throw new CompletionException(new CurseForgeException("HTTP 404: not found"));
                    }
                    if (status == 429) {
                        throw new CompletionException(new CurseForgeException("HTTP 429: rate limited"));
                    }
                    if (status < 200 || status >= 300) {
                        throw new CompletionException(new CurseForgeException("HTTP " + status));
                    }
                    try {
                        JsonElement element = JsonParser.parseString(body);
                        if (!element.isJsonObject()) {
                            throw new CurseForgeException("Malformed response");
                        }
                        return element.getAsJsonObject();
                    } catch (CurseForgeException error) {
                        throw new CompletionException(error);
                    } catch (Throwable error) {
                        throw new CompletionException(new CurseForgeException("Malformed response", error));
                    }
                })
                .exceptionallyCompose(error -> {
                    Throwable cause = unwrap(error);
                    if (cause instanceof CurseForgeException) {
                        return CompletableFuture.failedFuture(cause);
                    }
                    return CompletableFuture.failedFuture(new CurseForgeException(
                            cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause));
                });
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    // --------------------------------------------------------------- json utils

    private static JsonObject object(JsonObject root, String key) {
        if (root == null || !root.has(key)) return null;
        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject root, String key) {
        if (root == null || !root.has(key)) return null;
        JsonElement element = root.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        String value = element.getAsString();
        return value == null ? fallback : value;
    }

    private static long longValue(JsonObject root, String key, long fallback) {
        if (root == null || !root.has(key)) return fallback;
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsLong();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int intValue(JsonObject root, String key, int fallback) {
        long value = longValue(root, key, fallback);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    private static long parseDate(String iso) {
        if (iso == null || iso.isBlank()) return 0L;
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /** RFC 3986 路径段编码(空格 -> %20)。 */
    public static String encodePathSegment(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int b = raw & 0xFF;
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9')
                    || b == '-' || b == '.' || b == '_' || b == '~') {
                builder.append((char) b);
            } else {
                builder.append('%');
                builder.append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)));
                builder.append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
            }
        }
        return builder.toString();
    }

    private static String encodeQuery(String value) {
        return encodePathSegment(value);
    }
}
