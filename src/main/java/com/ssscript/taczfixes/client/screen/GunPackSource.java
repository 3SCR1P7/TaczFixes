package com.ssscript.taczfixes.client.screen;

import com.ssscript.taczfixes.common.data.GunPackDependencies;
import com.ssscript.taczfixes.common.data.GunPackInfoScanner;
import com.ssscript.taczfixes.common.data.GunPackResources;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
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
    private static final int FORMAT = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);

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
                    packs.add(createPack(info, FORMAT));
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
    private static Pack createPack(GunPackInfoScanner.PackInfoData info, int format) {
        // 用 format-1 让原版 PackCompatibility 判定为 TOO_OLD(红色“不兼容”), 与 1.21.1 版本一致。
        int packFormat = info.incompatible() ? format - 1 : format;
        Pack.Info packInfo = new Pack.Info(info.description(), packFormat, FeatureFlags.DEFAULT_FLAGS);
        return Pack.create(info.id(), title(info), false,
                id -> new GunPackResources(info.id(), info.path(), info.iconPath(), info.logo()),
                packInfo, PackType.CLIENT_RESOURCES, Pack.Position.TOP, false, PackSource.DEFAULT);
    }

    /** 不兼容枪包的标题: 红色标记 + 第一个依赖问题原因。 */
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
