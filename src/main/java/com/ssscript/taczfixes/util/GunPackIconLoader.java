package com.ssscript.taczfixes.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class GunPackIconLoader {
    public record LoadedIcon(ResourceLocation texture, int width, int height) {
    }

    private static final Map<ResourceLocation, LoadedIcon> CACHE = new HashMap<>();

    private GunPackIconLoader() {
    }

    public static LoadedIcon load(ResourceLocation icon) {
        if (CACHE.containsKey(icon)) return CACHE.get(icon);
        NativeImage image = readImage(icon);
        if (image == null) {
            CACHE.put(icon, null);
            return null;
        }
        ResourceLocation registered = new ResourceLocation("taczfixes", "icon_" + icon.getNamespace() + "_" + icon.getPath().replace('/', '_'));
        Minecraft.getInstance().getTextureManager().register(registered, new DynamicTexture(image));
        LoadedIcon loaded = new LoadedIcon(registered, image.getWidth(), image.getHeight());
        CACHE.put(icon, loaded);
        return loaded;
    }

    private static NativeImage readImage(ResourceLocation icon) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        Path taczDir = gameDir.resolve("tacz");
        if (!Files.isDirectory(taczDir)) return null;
        String relative = "assets/" + icon.getNamespace() + "/textures/" + icon.getPath() + ".png";
        try (java.util.stream.Stream<Path> children = Files.list(taczDir)) {
            java.util.List<Path> list = children.sorted().collect(java.util.stream.Collectors.toList());
            for (Path child : list) {
                NativeImage image = readFromPath(child, relative);
                if (image != null) return image;
            }
        } catch (Exception e) {
            com.ssscript.taczfixes.TaczFixesMod.LOGGER.warn("taczfixes: gun pack scan failed for {}", icon, e);
        }
        return null;
    }

    private static NativeImage readFromPath(Path child, String relative) {
        try {
            if (Files.isDirectory(child)) {
                Path file = child.resolve(relative);
                if (!Files.isRegularFile(file)) return null;
                try (InputStream in = Files.newInputStream(file)) {
                    return NativeImage.read(NativeImage.Format.RGBA, in);
                }
            } else if (Files.isRegularFile(child)) {
                try (ZipFile zip = new ZipFile(child.toFile())) {
                    ZipEntry entry = zip.getEntry(relative);
                    if (entry == null) return null;
                    try (InputStream in = zip.getInputStream(entry)) {
                        return NativeImage.read(NativeImage.Format.RGBA, in);
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}