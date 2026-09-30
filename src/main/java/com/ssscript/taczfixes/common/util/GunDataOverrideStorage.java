package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 编辑后 data 的持久化覆盖: 保存到 config/taczfixes, 启动/重载时替换进源枪包(zip/目录)。 */
public class GunDataOverrideStorage {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes");

    private GunDataOverrideStorage() {
    }

    private static Path storageDir() {
        return FMLPaths.GAMEDIR.get().resolve("config").resolve("taczfixes").resolve("gun_data");
    }

    private static Path storageFile(ResourceLocation dataId) {
        // '~' 不在命名空间/路径的合法字符内, 作为命名空间分隔符与路径斜杠, 避免名称含 '_' 时解析歧义
        return storageDir().resolve(dataId.getNamespace() + "~" +
                dataId.getPath().replace('/', '~') + ".json");
    }

    /** 保存编辑后的 data 全文到 config/taczfixes(一次, 启动时回放)。 */
    public static boolean save(ResourceLocation dataId, String fullText) {
        if (dataId == null || fullText == null || fullText.isBlank()) return false;
        try {
            Path dir = storageDir();
            Files.createDirectories(dir);
            Files.writeString(storageFile(dataId), fullText, StandardCharsets.UTF_8);
            return true;
        } catch (Exception ex) {
            LOGGER.warn("taczfixes: failed to save gun data override {}", dataId, ex);
            return false;
        }
    }

    public static boolean save(ItemStack gunItem, String fullText) {
        if (gunItem == null || gunItem.isEmpty()) return false;
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) return false;
        ResourceLocation gunId = gun.getGunId(gunItem);
        if (gunId == null) return false;
        return save(TaczFixesDataManager.resolveDataId(gunId), fullText);
    }

    /** 启动/重载时应用所有覆盖并清理。 */
    public static void applyAll() {
        try {
            Path dir = storageDir();
            if (!Files.isDirectory(dir)) {
                return;
            }
            List<Path> files = new ArrayList<>();
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(files::add);
            }
            if (files.isEmpty()) {
                return;
            }
            for (Path file : files) {
                String fullText = Files.readString(file, StandardCharsets.UTF_8);
                boolean applied = applyToPacks(file.getFileName().toString(), fullText);
                if (applied) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (Exception ex) {
            LOGGER.error("taczfixes: failed to apply gun data overrides", ex);
        }
    }

    /** 将覆盖文件写入 tacz 包(zip/目录)。文件名形如 ns~pathtofile.json (~为路径斜杠); 兼容旧 ns_pathtofile.json。 */
    private static boolean applyToPacks(String fileName, String fullText) {
        String stem = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
        List<String[]> candidates = candidateIds(stem);
        Path taczDir = FMLPaths.GAMEDIR.get().resolve("tacz");
        if (candidates.isEmpty() || !Files.isDirectory(taczDir)) return false;
        List<Path> packs = new ArrayList<>();
        try (Stream<Path> entries = Files.list(taczDir)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                if (Files.isDirectory(entry) || entry.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    packs.add(entry);
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("taczfixes: failed to iterate tacz packs", ex);
            return false;
        }
        // 逐候选(dataId 解释)探测所有枪包, 取第一个真实存在的解释应用
        for (String[] candidate : candidates) {
            boolean any = false;
            for (Path pack : packs) {
                boolean handled = Files.isDirectory(pack)
                        ? replaceInDirPack(pack, candidate[0], candidate[1], fullText)
                        : replaceInZipPack(pack, candidate[0], candidate[1], fullText);
                if (handled) {
                    any = true;
                }
            }
            if (any) {
                return true;
            }
        }
        LOGGER.warn("taczfixes: gun data override {} did not match any pack", fileName);
        return false;
    }

    /** 从文件名主干解析候选 dataId: 新格式 ns~path 优先; 旧格式 ns_path 下划线位置存在歧义, 逐位置探测。 */
    private static List<String[]> candidateIds(String stem) {
        List<String[]> candidates = new ArrayList<>();
        int tilde = stem.indexOf('~');
        if (tilde > 0) {
            candidates.add(new String[]{stem.substring(0, tilde), stem.substring(tilde + 1).replace('~', '/') + ".json"});
        }
        for (int i = 0; i < stem.length(); i++) {
            if (stem.charAt(i) != '_' || i == 0) continue;
            String ns = stem.substring(0, i);
            if (ns.indexOf('~') >= 0) continue;
            String path = stem.substring(i + 1).replace('~', '/') + ".json";
            boolean duplicate = candidates.stream().anyMatch(c -> c[0].equals(ns) && c[1].equals(path));
            if (!duplicate) {
                candidates.add(new String[]{ns, path});
            }
        }
        return candidates;
    }

    private static boolean replaceInDirPack(Path packDir, String ns, String path, String editText) {
        try {
            Path target = packDir.resolve("data").resolve(ns).resolve("data").resolve("guns").resolve(path);
            if (!Files.exists(target)) return false;
            Files.writeString(target, editText, StandardCharsets.UTF_8);
            return true;
        } catch (Exception ex) {
            LOGGER.warn("taczfixes: failed to replace dir pack {}", packDir, ex);
            return false;
        }
    }

    private static boolean replaceInZipPack(Path zipFile, String ns, String path, String fullText) {
        String entryName = "data/" + ns + "/data/guns/" + path;
        boolean found = false;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile.toFile())) {
            found = zip.getEntry(entryName) != null;
        } catch (Exception ex) {
            LOGGER.warn("taczfixes: failed to inspect zip pack {}", zipFile, ex);
            return false;
        }
        if (!found) return false;

        Path tmp = zipFile.resolveSibling(zipFile.getFileName().toString() + ".tmp");
        try (
                java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile.toFile());
                ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))
        ) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                ZipEntry copy = new ZipEntry(e.getName());
                out.putNextEntry(copy);
                if (e.getName().equals(entryName)) {
                    out.write(fullText.getBytes(StandardCharsets.UTF_8));
                } else {
                    try (InputStream is = zip.getInputStream(e)) {
                        is.transferTo(out);
                    }
                }
                out.closeEntry();
            }
        } catch (Exception ex) {
            LOGGER.warn("taczfixes: failed to rewrite zip pack {}", zipFile, ex);
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            return false;
        }
        try {
            Files.move(tmp, zipFile, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception ex) {
            LOGGER.warn("taczfixes: failed to replace zip pack {}", zipFile, ex);
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            return false;
        }
    }
}
