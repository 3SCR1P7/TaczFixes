package com.ssscript.taczfixes.client.screen;

import com.ssscript.taczfixes.common.data.GunPackDependencies;
import com.ssscript.taczfixes.common.data.GunPackInfoScanner;
import com.ssscript.taczfixes.common.data.GunPackResources;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlags;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 枪包管理界面的包来源: 每次 {@code PackRepository#reload()} 都会重新扫描 tacz 目录,
 * 这样拖进管理界面的新枪包(原版会复制进 tacz 目录)能立刻出现在列表里。
 * 目录内容没变时按指纹复用上一次的扫描结果, 避免反复打开 zip 读取枪包信息。
 */
public final class GunPackSource implements RepositorySource {
    private final Path directory;
    private final String language;
    private List<GunPackInfoScanner.PackInfoData> packs = List.of();
    private List<Pack> cached = List.of();
    private String fingerprint;

    public GunPackSource(Path directory, String language) {
        this.directory = directory;
        this.language = language;
        this.fingerprint = fingerprint(this.directory, this.language);
    }

    /** 最近一次扫描到的全部枪包, 供界面刷新安装记录/更新红点等信息。 */
    public synchronized List<GunPackInfoScanner.PackInfoData> currentPacks() {
        resolvePacks();
        return this.packs;
    }

    @Override
    public void loadPacks(Consumer<Pack> consumer) {
        for (Pack pack : resolvePacks()) {
            consumer.accept(pack);
        }
    }

    private synchronized List<Pack> resolvePacks() {
        String current = fingerprint(this.directory, this.language);
        if (!current.equals(this.fingerprint) || this.cached.isEmpty()) {
            this.packs = GunPackInfoScanner.scan(this.directory, this.language);
            List<Pack> packs = new ArrayList<>(this.packs.size());
            for (GunPackInfoScanner.PackInfoData info : this.packs) {
                try {
                    Pack pack = createPack(info);
                    if (pack != null) {
                        packs.add(pack);
                    }
                } catch (Throwable ignored) {
                    // 单个枪包信息异常时跳过, 不能因此让整个管理界面/资源重载失败。
                }
            }
            this.cached = List.copyOf(packs);
            this.fingerprint = current;
        }
        return this.cached;
    }

    /** 目录指纹: 文件名 + 大小 + 修改时间。用于判断是否需要重新扫描。 */
    private static String fingerprint(Path directory, String language) {
        StringBuilder builder = new StringBuilder(language).append('|');
        List<String> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                    entries.add(path.getFileName() + "|" + attributes.size() + "|"
                            + attributes.lastModifiedTime().toMillis());
                } catch (IOException failed) {
                    entries.add(path.getFileName().toString());
                }
            }
        } catch (IOException missing) {
            return builder.toString();
        }
        entries.sort(String.CASE_INSENSITIVE_ORDER);
        for (String entry : entries) {
            builder.append(entry).append('\n');
        }
        return builder.toString();
    }

    /** 把扫描结果包装成原版资源包(依赖有问题的按原版“不兼容资源包”显示)。 */
    private static Pack createPack(GunPackInfoScanner.PackInfoData info) {
        // 1.21.1 的 PackCompatibility 只有 TOO_OLD/TOO_NEW/COMPATIBLE, 这里用 TOO_OLD 表示不兼容。
        Component packTitle = title(info);
        PackCompatibility compatibility = info.incompatible()
                ? PackCompatibility.TOO_OLD : PackCompatibility.COMPATIBLE;
        PackLocationInfo location = new PackLocationInfo(info.id(), packTitle,
                PackSource.DEFAULT, java.util.Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo loc) {
                return new GunPackResources(info.id(), info.path(), info.iconPath(), info.logo());
            }

            @Override
            public PackResources openFull(PackLocationInfo loc, Pack.Metadata metadata) {
                return openPrimary(loc);
            }
        };
        Pack.Metadata metadata = new Pack.Metadata(info.description(), compatibility,
                FeatureFlags.DEFAULT_FLAGS, List.of(), false);
        return new Pack(location, supplier, metadata,
                new PackSelectionConfig(false, Pack.Position.TOP, false));
    }

    /** 不兼容枪包的标题: 红色标记 + 第一个依赖问题原因(与 1.20.1 版本一致)。 */
    private static Component title(GunPackInfoScanner.PackInfoData info) {
        if (!info.incompatible()) {
            return info.displayName();
        }
        return Component.empty()
                .append(Component.translatable("gui.taczfixes.gun_packs.incompatible"))
                .append(" ")
                .append(info.displayName())
                .append(" ")
                .append(issueText(info.issues()));
    }

    private static Component issueText(List<GunPackDependencies.Issue> issues) {
        GunPackDependencies.Issue first = issues.get(0);
        Component reason = first.type() == GunPackDependencies.IssueType.MISSING
                ? Component.translatable("gui.taczfixes.gun_packs.issue.missing", first.modId())
                : Component.translatable("gui.taczfixes.gun_packs.issue.version",
                        first.modId(), first.range() == null ? "" : first.range());
        if (issues.size() > 1) {
            reason = Component.translatable("gui.taczfixes.gun_packs.issue.more", reason, issues.size() - 1);
        }
        return Component.literal("\u00a77(").append(reason).append(")");
    }
}
