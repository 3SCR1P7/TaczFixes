package com.ssscript.taczfixes.common.util;

import java.nio.file.Path;

/** Close TACZ/vanilla SharedZipFileAccess handles before rewriting a gun-pack zip on Windows. */
public final class ZipPackHandleCloser {
    private ZipPackHandleCloser() {
    }

    public static void closeHandles(Path zipFile) {
        if (zipFile == null) return;
        try {
            Class<?> closer = Class.forName("com.ssscript.taczfixes.client.util.GunPackZipHandleCloser");
            closer.getMethod("closeHandles", Path.class).invoke(null, zipFile);
        } catch (Throwable ignored) {
        }
    }
}
