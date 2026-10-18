package com.ssscript.taczfixes.common.data;

import java.nio.file.Path;

/** 枪包的唯一标识: 命名空间@文件名。命名空间相同的多个枪包仍能分别启用/禁用。 */
public final class GunPackIds {
    private GunPackIds() {
    }

    public static String of(String namespace, Path path) {
        String fileName = path == null || path.getFileName() == null ? "pack" : path.getFileName().toString();
        return namespace + "@" + fileName;
    }

    public static String namespaceOf(String id) {
        if (id == null) return "";
        int split = id.indexOf('@');
        return split < 0 ? id : id.substring(0, split);
    }

    public static boolean isNamespaceEntry(String entry) {
        return entry != null && !entry.contains("@");
    }
}
