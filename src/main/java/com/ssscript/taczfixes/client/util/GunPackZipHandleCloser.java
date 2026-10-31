package com.ssscript.taczfixes.client.util;

import net.minecraft.client.Minecraft;
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

/** Close the shared ZipFile held by TACZ's gun-pack PackResources before replacing the zip. */
public final class GunPackZipHandleCloser {
    private static final String SHARED_ZIP = "net.minecraft.server.packs.FilePackResources$SharedZipFileAccess";

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
        Class<?> type = value.getClass();
        if (SHARED_ZIP.equals(type.getName())) {
            closeIfMatching(value, target);
            return;
        }
        if (!(value instanceof PackResources) && !(value instanceof Collection<?>) && !(value instanceof Map<?, ?>)) {
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
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                field.setAccessible(true);
                Object child = field.get(value);
                if (child instanceof PackResources || child instanceof Collection<?> || child instanceof Map<?, ?>
                        || (child != null && SHARED_ZIP.equals(child.getClass().getName()))) {
                    walk(child, target, seen, depth + 1);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void closeIfMatching(Object shared, Path target) {
        try {
            Field fileField = shared.getClass().getDeclaredField("file");
            fileField.setAccessible(true);
            Object fieldValue = fileField.get(shared);
            if (!(fieldValue instanceof File file)) return;
            if (!file.toPath().toAbsolutePath().normalize().equals(target)) return;
            ((AutoCloseable) shared).close();
        } catch (Throwable ignored) {
        }
    }
}
