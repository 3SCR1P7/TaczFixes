package com.ssscript.taczfixes.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Close the {@link java.util.zip.ZipFile} held by a gun-pack {@link FilePackResources} before the
 * underlying zip is replaced/re-walked on Windows.  The file pack reopens the zip lazily, so closing
 * it here only releases the OS handle.
 */
public final class GunPackZipHandleCloser {
    private GunPackZipHandleCloser() {
    }

    public static void closeHandles(Path zipFile) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        ResourceManager manager = minecraft.getResourceManager();
        if (manager == null) return;
        Path target = zipFile.toAbsolutePath().normalize();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PackResources pack : manager.listPacks().toList()) {
            walk(pack, target, seen, 0);
        }
    }

    private static void walk(Object value, Path target, Set<Object> seen, int depth) {
        if (value == null || depth > 6 || !seen.add(value)) return;
        if (value instanceof FilePackResources filePack) {
            closeIfMatching(filePack, target);
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object child : collection) walk(child, target, seen, depth + 1);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object child : map.values()) walk(child, target, seen, depth + 1);
            return;
        }
        if (!(value instanceof PackResources)) return;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                field.setAccessible(true);
                Object child = field.get(value);
                if (child instanceof PackResources || child instanceof Collection<?> || child instanceof Map<?, ?>) {
                    walk(child, target, seen, depth + 1);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Identify the backing file by type instead of by field name so the code keeps working under
     * both parchment (dev) and SRG (production) mappings.
     */
    private static void closeIfMatching(FilePackResources pack, Path target) {
        try {
            for (Field field : FilePackResources.class.getDeclaredFields()) {
                if (field.getType() != File.class) continue;
                field.setAccessible(true);
                Object value = field.get(pack);
                if (!(value instanceof File file)) continue;
                if (!file.toPath().toAbsolutePath().normalize().equals(target)) return;
                pack.close();
                return;
            }
        } catch (Throwable ignored) {
        }
    }
}
