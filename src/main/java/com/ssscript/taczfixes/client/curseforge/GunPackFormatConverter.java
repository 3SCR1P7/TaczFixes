package com.ssscript.taczfixes.client.curseforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * TaCZ 枪包新旧格式的检测与转换。
 *
 * <p>TaCZ 1.2.0 起枪包格式改为: zip 根目录必须有 {@code gunpack.meta.json}, 资源按资源包结构
 * 平铺在 {@code assets/<namespace>/} 与 {@code data/<namespace>/} 下。旧格式(1.1.x 及更早)则是
 * {@code <namespace>/pack.json} + {@code <namespace>/<type>/display/...}、{@code <namespace>/models/...}
 * 等目录。本类完全对齐 TaCZ 自带的 {@code /tacz convert} 命令(参考实现
 * {@code com.tacz.guns.resource.PackConvertor}), 供下载安装枪包时自动把旧格式转换 /
 * 把缺失 meta 的新格式补齐。</p>
 *
 * <p>与 TaCZ 参考实现的差异(均为任务要求或防御性改进):</p>
 * <ul>
 *     <li>meta JSON 的 {@code dependencies} 固定写空对象 {@code {}}, 而不是 TaCZ 里因传 null 而被 Gson 省略;</li>
 *     <li>配方 JSON 统一用 UTF-8 写出(TaCZ 用的是平台默认编码), 内容仍为对象且无条件写入
 *     {@code "type": "tacz:gun_smith_table_crafting"};</li>
 *     <li>拒绝任何包含 {@code ".."} 或以 {@code /} 开头的 zip 条目名(zip-slip 防御), 这类条目会被丢弃;</li>
 *     <li>转换结果先写同目录临时文件, 成功后再替换目标文件, 失败时删除临时文件, 不会留下半截 zip;</li>
 *     <li>多个条目映射到同一目标路径时保留第一个并记录警告(TaCZ 会因 ZipOutputStream 重复条目直接抛异常中断);</li>
 *     <li>额外兼容省略了类别层的旧包结构 {@code <ns>/display|index|data/<path>}: 这些条目 TaCZ 会丢弃,
 *     这里兜底映射到 {@code assets|data/<ns>/...}, 避免转换后枪包缺文件(不影响标准旧格式
 *     {@code <ns>/<type>/display/...} 的映射结果)。</li>
 * </ul>
 *
 * <p>本类只依赖 JDK 17 + Gson, 不引用任何 Minecraft / Forge 类, 因此可以在没有 Forge 的独立环境里编译与测试。</p>
 */
public final class GunPackFormatConverter {
    /** 新版格式的根目录元数据文件名。 */
    public static final String META_FILE_NAME = "gunpack.meta.json";

    /** 新版格式里旧版配方统一使用的配方类型。 */
    public static final String GUN_SMITH_TABLE_RECIPE_TYPE = "tacz:gun_smith_table_crafting";

    private static final int BUFFER_SIZE = 64 * 1024;

    private static final String CONVERTED_SUFFIX = "_converted.zip";
    private static final String META_ADDED_SUFFIX = "_meta.zip";
    private static final String TEMP_FILE_PREFIX = ".gunpack-convert-";
    private static final String TEMP_FILE_SUFFIX = ".zip.tmp";

    private static final System.Logger LOGGER = System.getLogger(GunPackFormatConverter.class.getName());

    /** 旧格式标识: zip 内 {@code <namespace>/pack.json}。组 1 = namespace。 */
    private static final Pattern LEGACY_PACK_INFO_PATTERN = Pattern.compile("^(\\w+)/pack\\.json$");

    /** 以下映射规则与 TaCZ {@code PackConvertor.LegacyPack} 中的正则逐字一致, 顺序也保持一致。 */
    private static final Pattern DISPLAY_PATTERN = Pattern.compile("^(\\w+)/(\\w+)/display/([\\w/.]+)$");
    private static final Pattern INDEX_PATTERN = Pattern.compile("^(\\w+)/(\\w+)/index/([\\w/.]+)$");
    private static final Pattern DATA_PATTERN = Pattern.compile("^(\\w+)/(\\w+)/data/([\\w/.]+)$");
    private static final Pattern MODELS_PATTERN = Pattern.compile("^(\\w+)/models/([\\w/.]+)$");
    private static final Pattern LANG_PATTERN = Pattern.compile("^(\\w+)/lang/([\\w/.]+)$");
    private static final Pattern ANIMATION_PATTERN = Pattern.compile("^(\\w+)/animations/([\\w/.]+)$");
    private static final Pattern TEXTURE_PATTERN = Pattern.compile("^(\\w+)/textures/([\\w/.]+)$");
    private static final Pattern SOUND_PATTERN = Pattern.compile("^(\\w+)/sounds/([\\w/.]+)$");
    private static final Pattern PLAYER_ANIMATOR_PATTERN = Pattern.compile("^(\\w+)/player_animator/([\\w/.]+)$");
    private static final Pattern TAGS_PATTERN = Pattern.compile("^(\\w+)/tags/([\\w/.]+)$");
    private static final Pattern RECIPE_PATTERN = Pattern.compile("^(\\w+)/recipes/([\\w/.]+)$");

    /**
     * "新格式布局但缺少 gunpack.meta.json" 的识别规则: {@code assets/<namespace>/gunpack_info.json}
     * 必须位于 zip 根目录。TaCZ 通过 SecureJar/PathPackResources 从 zip 根解析 assets,
     * 多套一层文件夹的 zip 即使补上 meta 也无法加载, 因此这里不接受外层包装。
     */
    private static final Pattern NEW_LAYOUT_INFO_PATTERN =
            Pattern.compile("^assets/([\\w.-]+)/gunpack_info\\.json$");

    /** addMissingMeta 接受的 namespace 字符集(不含路径分隔符, 防止拼出越界路径)。 */
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");

    /** 与 TaCZ 相同: 配方 JSON 美化输出。 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** meta JSON 用紧凑输出, 便于外部直接读取与断言。 */
    private static final Gson COMPACT_GSON = new Gson();

    /**
     * 旧格式到新格式的普通(原样复制字节)映射表, 顺序与 TaCZ 一致:
     * display -> index -> data -> models -> lang -> animations -> textures -> sounds -> player_animator -> tags。
     * 不在此表里的 recipes / pack.json 需要额外处理, 由后面的专用方法负责。
     */
    private static final List<Mapping> SIMPLE_MAPPINGS = List.of(
            new Mapping(DISPLAY_PATTERN, matcher -> "assets/" + matcher.group(1) + "/display/"
                    + matcher.group(2) + "/" + matcher.group(3)),
            new Mapping(INDEX_PATTERN, matcher -> "data/" + matcher.group(1) + "/index/"
                    + matcher.group(2) + "/" + matcher.group(3)),
            new Mapping(DATA_PATTERN, matcher -> "data/" + matcher.group(1) + "/data/"
                    + matcher.group(2) + "/" + matcher.group(3)),
            new Mapping(MODELS_PATTERN, matcher -> "assets/" + matcher.group(1) + "/geo_models/"
                    + matcher.group(2)),
            new Mapping(LANG_PATTERN, matcher -> "assets/" + matcher.group(1) + "/lang/"
                    + matcher.group(2)),
            new Mapping(ANIMATION_PATTERN, matcher -> "assets/" + matcher.group(1) + "/animations/"
                    + matcher.group(2)),
            new Mapping(TEXTURE_PATTERN, matcher -> "assets/" + matcher.group(1) + "/textures/"
                    + matcher.group(2)),
            new Mapping(SOUND_PATTERN, matcher -> "assets/" + matcher.group(1) + "/tacz_sounds/"
                    + matcher.group(2)),
            new Mapping(PLAYER_ANIMATOR_PATTERN, matcher -> "assets/" + matcher.group(1) + "/player_animator/"
                    + matcher.group(2)),
            new Mapping(TAGS_PATTERN, matcher -> "data/" + matcher.group(1) + "/tacz_tags/"
                    + matcher.group(2))
    );

    /**
     * 兼容映射: 少数旧版枪包把类别层(type)省略了, 直接写成 {@code <namespace>/display/<path>}
     * (TaCZ 的正式旧格式是 {@code <namespace>/<type>/display/<path>}, type = guns/ammo/attachments/blocks)。
     *
     * <p>这些条目在 TaCZ 的转换器里匹配不上任何规则、会被直接丢弃, 导致转换后枪包缺文件。
     * 这里作为兜底恢复它们; 只在前面所有正式规则都没命中时才尝试, 因此不会影响标准旧包的转换结果。</p>
     */
    private static final List<Mapping> RECOVERY_MAPPINGS = List.of(
            new Mapping(Pattern.compile("^(\\w+)/display/([\\w/.]+)$"),
                    matcher -> "assets/" + matcher.group(1) + "/display/" + matcher.group(2)),
            new Mapping(Pattern.compile("^(\\w+)/index/([\\w/.]+)$"),
                    matcher -> "data/" + matcher.group(1) + "/index/" + matcher.group(2)),
            new Mapping(Pattern.compile("^(\\w+)/data/([\\w/.]+)$"),
                    matcher -> "data/" + matcher.group(1) + "/data/" + matcher.group(2))
    );

    private GunPackFormatConverter() {
    }

    /** 枪包格式。 */
    public enum Format {
        /** 新版格式: zip 根目录存在 {@code gunpack.meta.json}。 */
        NEW,
        /** 旧版格式: zip 内存在 {@code <namespace>/pack.json}, 且根目录没有 {@code gunpack.meta.json}。 */
        LEGACY,
        /** 无法识别, 或文件不是可读的 zip。 */
        UNKNOWN
    }

    /**
     * 检测 zip 枪包的格式。
     *
     * <p>该方法只做结构判断, 不解析 pack.json 的内容; 读取 zip 失败(文件不存在 / 不是 zip)时返回
     * {@link Format#UNKNOWN} 而不是抛异常, 方便调用方先探测再决定是否转换。</p>
     *
     * @param zipFile 枪包 zip 路径
     * @return {@link Format#NEW} / {@link Format#LEGACY} / {@link Format#UNKNOWN}
     */
    public static Format detect(Path zipFile) {
        if (zipFile == null) {
            return Format.UNKNOWN;
        }
        try {
            ZipScan scan = scan(zipFile);
            if (scan.hasMeta()) {
                return Format.NEW;
            }
            if (scan.legacyNamespace() != null) {
                return Format.LEGACY;
            }
            return Format.UNKNOWN;
        } catch (IOException e) {
            return Format.UNKNOWN;
        }
    }

    /**
     * 是否是"新格式布局, 但根目录缺少 gunpack.meta.json"的枪包。
     *
     * <p>判定条件: 不存在根目录 {@code gunpack.meta.json}, 且存在
     * {@code assets/<namespace>/gunpack_info.json}(允许外面多包一层文件夹)。</p>
     *
     * @param zip 枪包 zip 路径
     * @return 满足上述条件返回 true; 文件不可读、不是 zip 或缺少 gunpack_info.json 都返回 false
     */
    public static boolean looksLikeNewLayoutZip(Path zip) {
        if (zip == null) {
            return false;
        }
        try {
            ZipScan scan = scan(zip);
            return !scan.hasMeta() && scan.newLayoutNamespace() != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 找出"新格式布局" zip 里的 namespace(来自 {@code assets/<namespace>/gunpack_info.json})。
     *
     * @param zip 枪包 zip 路径
     * @return 找到的 namespace; 没有该布局时返回 null
     * @throws IOException zip 无法读取
     */
    public static String findNewLayoutNamespace(Path zip) throws IOException {
        Objects.requireNonNull(zip, "zip");
        return scan(zip).newLayoutNamespace();
    }

    /**
     * 把旧格式枪包转换成新格式 zip。
     *
     * <p>输出 zip 的第一个条目固定是根目录 {@code gunpack.meta.json}:
     * {@code {"namespace":"<ns>","dependencies":{}}}; 随后按 TaCZ 的映射规则重排所有资源条目,
     * 匹配不上任何规则的条目(例如旧包里残留的说明文件)会被丢弃, 与 TaCZ 行为一致。</p>
     *
     * <p>namespace 取 zip 中第一个匹配 {@code <namespace>/pack.json} 的条目(遍历顺序即 zip 中央目录顺序)。
     * 若一个 zip 混有多个 namespace, 各条目仍按自己的 namespace 映射(与 TaCZ 相同), 只有 meta 用第一个。</p>
     *
     * @param legacyZip 旧格式枪包 zip
     * @param outputZip 输出 zip; 已存在会被替换。结果先写临时文件再移动, 失败时不会留下半截文件
     * @return 传入的 outputZip
     * @throws IOException 源 zip 不是旧格式 / 已读不到 / 写入失败
     */
    public static Path convertToNewFormat(Path legacyZip, Path outputZip) throws IOException {
        Objects.requireNonNull(legacyZip, "legacyZip");
        Objects.requireNonNull(outputZip, "outputZip");
        Path source = legacyZip.toAbsolutePath().normalize();
        Path target = outputZip.toAbsolutePath().normalize();
        if (source.equals(target)) {
            throw new IOException("output zip must not overwrite the source zip: " + source);
        }

        ZipScan scan = scan(source);
        if (scan.hasMeta()) {
            throw new IOException("source zip is already in the new gun pack format: " + displayName(source));
        }
        String namespace = scan.legacyNamespace();
        if (namespace == null) {
            throw new IOException("unsupported gun pack format (no <namespace>/pack.json entry): "
                    + displayName(source));
        }
        // 与 TaCZ PackConvertor.fromZipFile 一致: pack.json 必须能解析成 JSON 对象, 否则视为不是枪包
        validateLegacyPackJson(source, namespace);

        writeZipAtomically(target, out -> {
            Set<String> written = new HashSet<>();
            writeMetaEntry(out, namespace, written);

            try (ZipFile oldPack = new ZipFile(source.toFile(), StandardCharsets.UTF_8)) {
                Enumeration<? extends ZipEntry> entries = oldPack.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String entryName = entry.getName();
                    if (entryName == null || isUnsafeEntryName(entryName)) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "skip unsafe entry in legacy gun pack: " + entryName + " (" + source + ")");
                        continue;
                    }
                    if (META_FILE_NAME.equals(entryName)) {
                        continue;
                    }
                    if (convertSimpleEntry(oldPack, entry, out, written)) {
                        continue;
                    }
                    if (convertRecoveryEntry(oldPack, entry, out, written)) {
                        continue;
                    }
                    if (convertRecipeEntry(oldPack, entry, out, written)) {
                        continue;
                    }
                    if (convertPackInfoEntry(oldPack, entry, out, written)) {
                        continue;
                    }
                    // 与 TaCZ 一致: 匹配不上任何规则的文件静默丢弃。
                }
            }
        });
        return outputZip;
    }

    /**
     * 给"新格式布局但缺少根目录 gunpack.meta.json"的 zip 补上 meta, 其余条目原样复制。
     *
     * <p>源 zip 里若已有根目录 gunpack.meta.json 会被跳过(用传入的 namespace 重新生成)。
     * 用于部分制作者打包时漏放 meta 的枪包。</p>
     *
     * @param zipIn     源 zip
     * @param zipOut    输出 zip; 已存在会被替换
     * @param namespace 要写入 meta 的 namespace, 一般来自 {@link #findNewLayoutNamespace(Path)} 的结果
     * @return 传入的 zipOut
     * @throws IOException 源 zip 不可读 / 写入失败
     */
    public static Path addMissingMeta(Path zipIn, Path zipOut, String namespace) throws IOException {
        Objects.requireNonNull(zipIn, "zipIn");
        Objects.requireNonNull(zipOut, "zipOut");
        String checkedNamespace = requireValidNamespace(namespace);
        Path source = zipIn.toAbsolutePath().normalize();
        Path target = zipOut.toAbsolutePath().normalize();
        if (source.equals(target)) {
            throw new IOException("output zip must not overwrite the source zip: " + source);
        }
        // 先扫一遍, 保证源文件可读(损坏的 zip 在这里就以 IOException 暴露出来)。
        scan(source);

        writeZipAtomically(target, out -> {
            Set<String> written = new HashSet<>();
            writeMetaEntry(out, checkedNamespace, written);

            try (ZipFile src = new ZipFile(source.toFile(), StandardCharsets.UTF_8)) {
                Enumeration<? extends ZipEntry> entries = src.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String entryName = entry.getName();
                    if (META_FILE_NAME.equals(entryName)) {
                        continue;
                    }
                    if (entryName == null || isUnsafeEntryName(entryName)) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "skip unsafe entry when adding gun pack meta: " + entryName + " (" + source + ")");
                        continue;
                    }
                    if (!written.add(entryName)) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "skip duplicate zip entry when adding gun pack meta: " + entryName);
                        continue;
                    }
                    copyEntry(src, entry, out, entryName);
                }
            }
        });
        return zipOut;
    }

    /**
     * 保证枪包是可被 TaCZ 加载的新格式, 必要时转换 / 补 meta。
     *
     * <ul>
     *     <li>{@link Format#NEW}: 原样返回 {@code (zipIn, false, false)};</li>
     *     <li>{@link Format#LEGACY}: 转换到 {@code workDir/<name>_converted.zip}, 返回 {@code (..., true, false)};</li>
     *     <li>新格式布局但缺 meta: 补到 {@code workDir/<name>_meta.zip}, 返回 {@code (..., false, true)};</li>
     *     <li>其它: 抛 {@link IOException}("unsupported gun pack format")。</li>
     * </ul>
     *
     * @param zipIn   待检查的枪包 zip
     * @param workDir 转换产物目录(不存在会自动创建)
     * @return 转换结果
     * @throws IOException 不支持的格式 / 读写失败
     */
    public static ConversionResult ensureNewFormat(Path zipIn, Path workDir) throws IOException {
        Objects.requireNonNull(zipIn, "zipIn");
        Objects.requireNonNull(workDir, "workDir");

        ZipScan scan = scan(zipIn);
        if (scan.hasMeta()) {
            return new ConversionResult(zipIn, false, false);
        }
        if (scan.legacyNamespace() != null) {
            Path converted = resolveWorkFile(zipIn, workDir, CONVERTED_SUFFIX);
            convertToNewFormat(zipIn, converted);
            return new ConversionResult(converted, true, false);
        }
        if (scan.newLayoutNamespace() != null) {
            Path repaired = resolveWorkFile(zipIn, workDir, META_ADDED_SUFFIX);
            addMissingMeta(zipIn, repaired, scan.newLayoutNamespace());
            return new ConversionResult(repaired, false, true);
        }
        throw new IOException("unsupported gun pack format: " + displayName(zipIn));
    }

    /**
     * 一次 {@link #ensureNewFormat(Path, Path)} 的结果。
     *
     * @param file      可被 TaCZ 直接加载的 zip; 未改动时就是传入的原文件
     * @param converted 是否做了旧格式 -> 新格式转换
     * @param metaAdded 是否只是补写了 gunpack.meta.json
     */
    public record ConversionResult(Path file, boolean converted, boolean metaAdded) {
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 旧版普通条目的映射规则: 正则命中组 -> 新路径。 */
    private record Mapping(Pattern pattern, Function<Matcher, String> destination) {
    }

    /** zip 结构扫描结果。 */
    private record ZipScan(boolean hasMeta, String legacyNamespace, String newLayoutNamespace) {
    }

    /**
     * 校验旧包的 {@code <namespace>/pack.json} 是可解析的 JSON 对象。
     * TaCZ 的转换器在解析失败时会跳过这个包, 这里同样拒绝, 避免把无关 zip 转成空枪包装进游戏。
     */
    private static void validateLegacyPackJson(Path zip, String namespace) throws IOException {
        String path = namespace + "/pack.json";
        try (ZipFile zipFile = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipFile.getEntry(path);
            if (entry == null) {
                throw new IOException("legacy pack.json missing: " + path);
            }
            try (InputStream in = zipFile.getInputStream(entry);
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject info = GSON.fromJson(reader, JsonObject.class);
                if (info == null) {
                    throw new IOException("legacy pack.json is empty: " + path);
                }
            } catch (JsonParseException parseError) {
                throw new IOException("legacy pack.json is not valid JSON: " + path, parseError);
            }
        }
    }

    /** 写 zip 内容的回调(内容失败时由 {@link #writeZipAtomically} 负责清理)。 */
    @FunctionalInterface
    private interface ZipContentWriter {
        void write(ZipOutputStream out) throws IOException;
    }

    private static ZipScan scan(Path zip) throws IOException {
        if (!Files.isRegularFile(zip)) {
            throw new IOException("gun pack file does not exist or is not a regular file: " + zip);
        }
        boolean hasMeta = false;
        String legacyNamespace = null;
        String newLayoutNamespace = null;
        try (ZipFile zipFile = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (META_FILE_NAME.equals(name)) {
                    hasMeta = true;
                    continue;
                }
                if (legacyNamespace == null) {
                    Matcher matcher = LEGACY_PACK_INFO_PATTERN.matcher(name);
                    if (matcher.matches()) {
                        legacyNamespace = matcher.group(1);
                    }
                }
                if (newLayoutNamespace == null) {
                    Matcher matcher = NEW_LAYOUT_INFO_PATTERN.matcher(name);
                    if (matcher.matches()) {
                        newLayoutNamespace = matcher.group(1);
                    }
                }
            }
        }
        return new ZipScan(hasMeta, legacyNamespace, newLayoutNamespace);
    }

    private static void writeMetaEntry(ZipOutputStream out, String namespace, Set<String> written)
            throws IOException {
        if (!written.add(META_FILE_NAME)) {
            return;
        }
        JsonObject meta = new JsonObject();
        meta.addProperty("namespace", namespace);
        meta.add("dependencies", new JsonObject());
        byte[] bytes = COMPACT_GSON.toJson(meta).getBytes(StandardCharsets.UTF_8);
        out.putNextEntry(new ZipEntry(META_FILE_NAME));
        out.write(bytes);
        out.closeEntry();
    }

    private static boolean convertSimpleEntry(ZipFile oldPack, ZipEntry entry, ZipOutputStream out,
                                              Set<String> written) throws IOException {
        String entryName = entry.getName();
        for (Mapping mapping : SIMPLE_MAPPINGS) {
            Matcher matcher = mapping.pattern().matcher(entryName);
            if (!matcher.matches()) {
                continue;
            }
            String destination = mapping.destination().apply(matcher);
            copyMappedEntry(oldPack, entry, out, destination, written);
            return true;
        }
        return false;
    }

    private static boolean convertRecoveryEntry(ZipFile oldPack, ZipEntry entry, ZipOutputStream out,
                                                Set<String> written) throws IOException {
        String entryName = entry.getName();
        for (Mapping mapping : RECOVERY_MAPPINGS) {
            Matcher matcher = mapping.pattern().matcher(entryName);
            if (!matcher.matches()) {
                continue;
            }
            String destination = mapping.destination().apply(matcher);
            copyMappedEntry(oldPack, entry, out, destination, written);
            return true;
        }
        return false;
    }

    private static boolean convertRecipeEntry(ZipFile oldPack, ZipEntry entry, ZipOutputStream out,
                                              Set<String> written) throws IOException {
        Matcher matcher = RECIPE_PATTERN.matcher(entry.getName());
        if (!matcher.matches()) {
            return false;
        }
        String destination = "data/" + matcher.group(1) + "/recipes/" + matcher.group(2);
        if (isUnsafeEntryName(destination)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "skip recipe with unsafe destination: " + destination);
            return true;
        }

        JsonObject recipe;
        try (InputStream in = oldPack.getInputStream(entry);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            recipe = GSON.fromJson(reader, JsonObject.class);
        } catch (JsonParseException e) {
            // 与 TaCZ 一致: 解析失败只警告并丢弃该配方, 不影响整个枪包转换。
            LOGGER.log(System.Logger.Level.WARNING,
                    "skip unparsable legacy recipe: " + entry.getName(), e);
            return true;
        }
        if (recipe == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "skip empty legacy recipe: " + entry.getName());
            return true;
        }

        recipe.addProperty("type", GUN_SMITH_TABLE_RECIPE_TYPE);
        if (!written.add(destination)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "skip duplicate converted entry: " + destination);
            return true;
        }
        out.putNextEntry(new ZipEntry(destination));
        out.write(GSON.toJson(recipe).getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
        return true;
    }

    private static boolean convertPackInfoEntry(ZipFile oldPack, ZipEntry entry, ZipOutputStream out,
                                                Set<String> written) throws IOException {
        Matcher matcher = LEGACY_PACK_INFO_PATTERN.matcher(entry.getName());
        if (!matcher.matches()) {
            return false;
        }
        String destination = "assets/" + matcher.group(1) + "/gunpack_info.json";
        // pack.json 原样复制字节, 不解析也不重新序列化, 与 TaCZ 相同。
        copyMappedEntry(oldPack, entry, out, destination, written);
        return true;
    }

    private static void copyMappedEntry(ZipFile oldPack, ZipEntry entry, ZipOutputStream out,
                                        String destination, Set<String> written) throws IOException {
        if (isUnsafeEntryName(destination)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "skip converted entry with unsafe destination: " + destination);
            return;
        }
        if (!written.add(destination)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "skip duplicate converted entry: " + destination);
            return;
        }
        copyEntry(oldPack, entry, out, destination);
    }

    private static void copyEntry(ZipFile source, ZipEntry entry, ZipOutputStream out, String destination)
            throws IOException {
        try (InputStream in = source.getInputStream(entry)) {
            out.putNextEntry(new ZipEntry(destination));
            copyStream(in, out);
            out.closeEntry();
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /**
     * 先写同目录临时文件, 完整写完后再替换目标文件; 任何失败都会删除临时文件。
     * 这样下游绝不会读到半截 zip。
     */
    private static void writeZipAtomically(Path output, ZipContentWriter content) throws IOException {
        Path absoluteOutput = output.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = parent != null
                ? Files.createTempFile(parent, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX)
                : Files.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
        try {
            try (OutputStream fileOut = Files.newOutputStream(temp);
                 ZipOutputStream zipOut = new ZipOutputStream(fileOut, StandardCharsets.UTF_8)) {
                content.write(zipOut);
            }
            Files.move(temp, absoluteOutput, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException | Error e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    private static Path resolveWorkFile(Path zipIn, Path workDir, String suffix) throws IOException {
        Files.createDirectories(workDir);
        String fileName = zipIn.getFileName() == null ? "gunpack" : zipIn.getFileName().toString();
        String stem = fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? fileName.substring(0, fileName.length() - ".zip".length())
                : fileName;
        if (stem.isBlank()) {
            stem = "gunpack";
        }
        return workDir.resolve(stem + suffix);
    }

    private static String requireValidNamespace(String namespace) throws IOException {
        Objects.requireNonNull(namespace, "namespace");
        String trimmed = namespace.trim();
        if (trimmed.isEmpty()
                || !NAMESPACE_PATTERN.matcher(trimmed).matches()
                || trimmed.contains("..")) {
            throw new IOException("invalid gun pack namespace: " + namespace);
        }
        return trimmed;
    }

    /**
     * zip-slip 防御: 只接受相对、无回溯段的条目名。
     * 转换生成的新路径都来自正则捕获组, 但仍统一检查, 防止 {@code [\w/.]+} 捕获出 {@code a/../b}。
     */
    private static boolean isUnsafeEntryName(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        if (name.contains("..")) {
            return true;
        }
        if (name.charAt(0) == '/' || name.charAt(0) == '\\') {
            return true;
        }
        if (name.indexOf('\\') >= 0) {
            return true;
        }
        return name.indexOf('\0') >= 0;
    }

    private static String displayName(Path path) {
        Path fileName = path.getFileName();
        return fileName != null ? fileName.toString() : path.toString();
    }
}
