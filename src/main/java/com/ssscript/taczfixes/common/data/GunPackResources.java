package com.ssscript.taczfixes.common.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 把枪包目录/zip 包装成原版 PackResources, 并把根资源 pack.png 指向枪包的 logo
 * (logo 可能来自另一个枪包, 例如无 logo 时回退到 TaCZ 默认枪包), 使原版资源包界面能直接当图标用。
 */
public class GunPackResources implements PackResources {
    private final String packId;
    private final PackResources delegate;
    private final Path iconPath;
    private final ResourceLocation logo;

    public GunPackResources(String namespace, Path path, Path iconPath, ResourceLocation logo) {
        this.packId = "taczfixes_gunpack_" + namespace;
        this.delegate = Files.isDirectory(path)
                ? new PathPackResources(packId, path, false)
                : new FilePackResources(packId, path.toFile(), false);
        this.iconPath = iconPath;
        this.logo = logo;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        if (logo != null && iconPath != null && elements.length == 1 && "pack.png".equals(elements[0])) {
            IoSupplier<InputStream> icon = openIcon();
            if (icon != null) {
                return icon;
            }
        }
        return delegate.getRootResource(elements);
    }

    /** 直接从 logo 所属枪包(目录或 zip)读取图标文件。 */
    private IoSupplier<InputStream> openIcon() {
        try {
            String relative = "assets/" + logo.getNamespace() + "/" + logo.getPath();
            if (Files.isDirectory(iconPath)) {
                Path file = iconPath.resolve(relative);
                return Files.exists(file) ? IoSupplier.create(file) : null;
            }
            if (!Files.isRegularFile(iconPath)) {
                return null;
            }
            ZipFile zip = new ZipFile(iconPath.toFile(), StandardCharsets.UTF_8);
            ZipEntry entry = GunPackInfoScanner.findEntry(zip, relative);
            if (entry == null) {
                zip.close();
                return null;
            }
            return IoSupplier.create(zip, entry);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        return delegate.getResource(type, location);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        delegate.listResources(type, namespace, path, output);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return delegate.getNamespaces(type);
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) throws IOException {
        return delegate.getMetadataSection(deserializer);
    }

    @Override
    public String packId() {
        return packId;
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public void close() {
        delegate.close();
    }
}
